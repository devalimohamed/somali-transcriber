import React, { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import { useAuth } from '../context/AuthContext';
import { CallResponse } from '../types';

export function HistoryScreen() {
  const { apiClient } = useAuth();
  const [calls, setCalls] = useState<CallResponse[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadHistory = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await apiClient.listCalls();
      setCalls(data);
    } catch {
      setError('Failed to load call history.');
    } finally {
      setIsLoading(false);
    }
  }, [apiClient]);

  useEffect(() => {
    void loadHistory();
  }, [loadHistory]);

  return (
    <View style={styles.container}>
      <View style={styles.headerRow}>
        <Text style={styles.header}>History</Text>
        <Pressable style={styles.refreshButton} onPress={loadHistory}>
          <Text style={styles.refreshText}>Refresh</Text>
        </Pressable>
      </View>

      {isLoading ? <ActivityIndicator style={{ marginTop: 12 }} /> : null}
      {error ? <Text style={styles.error}>{error}</Text> : null}

      <FlatList
        data={calls}
        keyExtractor={(item) => item.callId}
        contentContainerStyle={styles.list}
        renderItem={({ item }) => (
          <View style={styles.card}>
            <Text style={styles.meta}>Call at {new Date(item.metadata.callAt).toLocaleString()}</Text>
            <Text style={styles.meta}>Status: {item.status}</Text>
            <Text style={styles.note}>{item.noteText ?? 'No summary yet'}</Text>
          </View>
        )}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f8f4ea',
    padding: 16
  },
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center'
  },
  header: {
    fontSize: 28,
    fontWeight: '800',
    color: '#111827'
  },
  refreshButton: {
    borderWidth: 1,
    borderColor: '#0f766e',
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 8
  },
  refreshText: {
    color: '#0f766e',
    fontWeight: '800',
    fontSize: 18
  },
  list: {
    paddingTop: 12,
    gap: 10
  },
  card: {
    backgroundColor: '#fff',
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#e5e7eb',
    padding: 12,
    gap: 4
  },
  meta: {
    color: '#4b5563',
    fontSize: 16,
    fontWeight: '600'
  },
  note: {
    color: '#1f2937',
    marginTop: 6,
    lineHeight: 30,
    fontSize: 20,
    fontWeight: '700'
  },
  error: {
    color: '#b91c1c',
    marginTop: 8,
    fontSize: 18,
    fontWeight: '600'
  }
});
