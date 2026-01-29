import React, { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { Audio } from 'expo-av';
import * as Clipboard from 'expo-clipboard';
import * as FileSystem from 'expo-file-system/legacy';
import * as Network from 'expo-network';
import { useAuth } from '../context/AuthContext';
import { API_BASE_URL } from '../api/client';
import { decryptBase64Audio, encryptBase64Audio } from '../services/crypto';
import { enqueueUpload, getPendingUploads, removeUpload } from '../services/offlineQueue';
import { CallResponse, CallStatus, PendingUpload } from '../types';

const MIME_TYPE = 'audio/mpeg';
const TERMINAL_PROCESSING_STATUSES: Set<CallStatus> = new Set([
  'READY',
  'READY_WITH_WARNING',
  'FAILED',
  'FINALIZED'
]);

function getErrorMessage(error: unknown): string {
  const raw = error instanceof Error && error.message ? error.message : 'Unknown error';
  const normalized = raw.trim();

  if (/Network request failed|Failed to fetch|Cannot reach backend/i.test(normalized)) {
    return `Cannot reach backend at ${API_BASE_URL}. If using iPhone, run ./run --device.`;
  }

  if (/Session expired|401|403/i.test(normalized)) {
    return 'Session expired. Please log out and log back in.';
  }

  return normalized;
}

export function RecorderScreen() {
  const { apiClient } = useAuth();
  const [recording, setRecording] = useState<Audio.Recording | null>(null);
  const [isWorking, setIsWorking] = useState(false);
  const [outputText, setOutputText] = useState('');
  const [copyState, setCopyState] = useState<'idle' | 'copied'>('idle');
  const copyResetTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const waitForProcessedCall = useCallback(
    async (callId: string): Promise<CallResponse | null> => {
      let latest: CallResponse | null = null;
      for (let attempt = 0; attempt < 30; attempt += 1) {
        latest = await apiClient.getCall(callId);
        if (TERMINAL_PROCESSING_STATUSES.has(latest.status)) {
          return latest;
        }
        await new Promise((resolve) => setTimeout(resolve, 1500));
      }
      return latest;
    },
    [apiClient]
  );

  const processQueue = useCallback(async () => {
    const net = await Network.getNetworkStateAsync();
    if (!net.isConnected) {
      setOutputText('Offline. Recording queued and will auto-upload later.');
      return;
    }

    const queued = await getPendingUploads();
    if (!queued.length) {
      return;
    }

    setOutputText(`Uploading ${queued.length} queued item(s)...`);

    for (const item of queued) {
      const tempUri = `${FileSystem.cacheDirectory}${item.id}${item.extension}`;
      try {
        const base64Audio = await decryptBase64Audio(item.encryptedAudio);
        await FileSystem.writeAsStringAsync(tempUri, base64Audio, {
          encoding: FileSystem.EncodingType.Base64
        });

        let response = await apiClient.uploadAudio(item.callId, tempUri, item.mimeType, item.durationSeconds);
        if (!TERMINAL_PROCESSING_STATUSES.has(response.status)) {
          setOutputText('Audio uploaded. Processing...');
          const latest = await waitForProcessedCall(item.callId);
          if (latest) {
            response = latest;
          }
        }

        setOutputText(response.noteText?.trim() || response.warning || response.status);
        await removeUpload(item.id);
      } catch (error) {
        setOutputText(`Upload failed: ${getErrorMessage(error)} Keeping item in offline queue.`);
        break;
      } finally {
        await FileSystem.deleteAsync(tempUri, { idempotent: true });
      }
    }
  }, [apiClient, waitForProcessedCall]);

  useEffect(() => {
    let mounted = true;

    const checkBackendAndQueue = async () => {
      const healthy = await apiClient.checkHealth();
      if (!mounted) {
        return;
      }

      if (!healthy) {
        setOutputText(`Backend not reachable at ${API_BASE_URL}. If on iPhone, use ./run --device.`);
        return;
      }

      await processQueue();
    };

    void checkBackendAndQueue();
    return () => {
      mounted = false;
      if (copyResetTimeoutRef.current) {
        clearTimeout(copyResetTimeoutRef.current);
      }
    };
  }, [apiClient, processQueue]);

  const startRecording = useCallback(async () => {
    const permission = await Audio.requestPermissionsAsync();
    if (!permission.granted) {
      setOutputText('Microphone permission is required.');
      return;
    }

    await Audio.setAudioModeAsync({
      allowsRecordingIOS: true,
      playsInSilentModeIOS: true
    });

    const newRecording = new Audio.Recording();
    await newRecording.prepareToRecordAsync(Audio.RecordingOptionsPresets.HIGH_QUALITY);
    await newRecording.startAsync();

    setRecording(newRecording);
    setOutputText('Recording... tap the button again to stop.');
  }, []);

  const stopRecording = useCallback(async () => {
    if (!recording) {
      return;
    }

    setIsWorking(true);
    try {
      await recording.stopAndUnloadAsync();
      const uri = recording.getURI();
      const status = await recording.getStatusAsync();
      const durationSeconds = Math.max(1, Math.round((status.durationMillis ?? 0) / 1000));
      setRecording(null);

      if (!uri) {
        setOutputText('Recording did not produce a file. Try again.');
        return;
      }

      setOutputText('Creating call record...');
      const call = await apiClient.createCall(new Date().toISOString());

      const base64Audio = await FileSystem.readAsStringAsync(uri, {
        encoding: FileSystem.EncodingType.Base64
      });
      const encryptedAudio = await encryptBase64Audio(base64Audio);

      const item: PendingUpload = {
        id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
        callId: call.callId,
        encryptedAudio,
        mimeType: MIME_TYPE,
        durationSeconds,
        extension: '.m4a'
      };

      await enqueueUpload(item);
      setOutputText('Recording queued. Processing upload...');

      await processQueue();
    } catch (error) {
      setOutputText(`Failed to process recording: ${getErrorMessage(error)}`);
    } finally {
      setIsWorking(false);
    }
  }, [apiClient, processQueue, recording]);

  const copyOutput = useCallback(async () => {
    const trimmedOutput = outputText.trim();
    if (!trimmedOutput) {
      return;
    }

    try {
      await Clipboard.setStringAsync(trimmedOutput);
      setCopyState('copied');
      if (copyResetTimeoutRef.current) {
        clearTimeout(copyResetTimeoutRef.current);
      }
      copyResetTimeoutRef.current = setTimeout(() => setCopyState('idle'), 1500);
    } catch (error) {
      setOutputText(`Failed to copy text: ${getErrorMessage(error)}`);
    }
  }, [outputText]);

  const hasOutput = outputText.trim().length > 0;

  return (
    <View style={styles.container}>
      <View style={styles.instructions}>
        <Text style={styles.title}>Record Call Note</Text>
        <Text style={styles.subtitle}>Tap the big button once to start. Tap again to stop and transcribe.</Text>
      </View>

      <Pressable
        style={[styles.recordButton, recording ? styles.stopButton : null, isWorking ? styles.disabled : null]}
        onPress={recording ? stopRecording : startRecording}
        disabled={isWorking}
      >
        {isWorking ? (
          <ActivityIndicator color="#fff" size="large" />
        ) : (
          <>
            <Text style={styles.recordText}>{recording ? 'STOP' : 'RECORD'}</Text>
            <Text style={styles.recordSubText}>{recording ? 'Tap to finish' : 'Tap to start'}</Text>
          </>
        )}
      </Pressable>

      <Pressable
        style={[styles.outputBox, !hasOutput ? styles.outputBoxEmpty : null]}
        onPress={() => void copyOutput()}
        disabled={!hasOutput}
      >
        <ScrollView contentContainerStyle={styles.outputScroll} showsVerticalScrollIndicator={false}>
          <Text style={[styles.outputText, !hasOutput ? styles.placeholderText : null]}>
            {hasOutput
              ? outputText
              : 'Your summary will appear here. Tap this box to copy when text is available.'}
          </Text>
        </ScrollView>
        {hasOutput ? <Text style={styles.copyHint}>{copyState === 'copied' ? 'Copied' : 'Tap to copy'}</Text> : null}
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingHorizontal: 18,
    paddingTop: 14,
    paddingBottom: 20,
    gap: 16,
    backgroundColor: '#f8f4ea'
  },
  instructions: {
    gap: 4
  },
  title: {
    color: '#0f172a',
    fontSize: 30,
    fontWeight: '800'
  },
  subtitle: {
    color: '#334155',
    fontSize: 18,
    fontWeight: '600',
    lineHeight: 24
  },
  recordButton: {
    backgroundColor: '#0f766e',
    width: 220,
    height: 220,
    borderRadius: 110,
    alignItems: 'center',
    justifyContent: 'center',
    alignSelf: 'center',
    borderWidth: 5,
    borderColor: '#134e4a',
    shadowColor: '#0f172a',
    shadowOffset: { width: 0, height: 6 },
    shadowOpacity: 0.2,
    shadowRadius: 10,
    elevation: 5
  },
  stopButton: {
    backgroundColor: '#be123c'
  },
  disabled: {
    opacity: 0.82
  },
  recordText: {
    color: '#fff',
    fontWeight: '800',
    fontSize: 34,
    letterSpacing: 1
  },
  recordSubText: {
    color: '#ecfeff',
    fontWeight: '700',
    fontSize: 18,
    marginTop: 6
  },
  outputBox: {
    minHeight: 160,
    maxHeight: 230,
    borderWidth: 1,
    borderColor: '#cbd5e1',
    borderRadius: 14,
    backgroundColor: '#fff',
    paddingHorizontal: 14,
    paddingVertical: 12
  },
  outputBoxEmpty: {
    borderStyle: 'dashed'
  },
  outputScroll: {
    paddingBottom: 4
  },
  outputText: {
    color: '#0f172a',
    fontSize: 20,
    lineHeight: 29,
    fontWeight: '700'
  },
  placeholderText: {
    color: '#475569',
    fontWeight: '600'
  },
  copyHint: {
    marginTop: 8,
    color: '#0f766e',
    fontWeight: '800',
    fontSize: 18
  }
});
