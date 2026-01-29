import React, { useEffect, useState } from 'react';
import * as SecureStore from 'expo-secure-store';
import {
  ActivityIndicator,
  Keyboard,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput
} from 'react-native';
import { useAuth } from '../context/AuthContext';

const USERNAME_STORAGE_KEY = 'somtranscriber_username';

export function LoginScreen() {
  const { login } = useAuth();
  const [username, setUsername] = useState('');
  const [pin, setPin] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    const loadUsername = async () => {
      const savedUsername = await SecureStore.getItemAsync(USERNAME_STORAGE_KEY);
      if (savedUsername) {
        setUsername(savedUsername);
      }
    };

    void loadUsername();
  }, []);

  const onSubmit = async () => {
    Keyboard.dismiss();
    const trimmedUsername = username.trim();
    const trimmedPin = pin.trim();

    if (!trimmedUsername) {
      setError('Username is required.');
      return;
    }

    if (!/^\d{6}$/.test(trimmedPin)) {
      setError('PIN must be exactly 6 digits.');
      return;
    }

    setError(null);
    setIsSubmitting(true);
    try {
      await login(trimmedUsername, trimmedPin);
      await SecureStore.setItemAsync(USERNAME_STORAGE_KEY, trimmedUsername);
    } catch {
      setError('Login failed. Check your credentials.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <KeyboardAvoidingView
      style={styles.keyboardContainer}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
    >
      <ScrollView
        contentContainerStyle={styles.container}
        keyboardShouldPersistTaps="handled"
        keyboardDismissMode="on-drag"
      >
        <Text style={styles.title}>SomTranscriber</Text>
        <Text style={styles.subtitle}>Sign in to create call summaries</Text>

        <TextInput
          style={styles.input}
          autoCapitalize="none"
          autoCorrect={false}
          placeholder="Username"
          value={username}
          onChangeText={setUsername}
        />
        <TextInput
          style={styles.input}
          secureTextEntry
          keyboardType="number-pad"
          maxLength={6}
          placeholder="6-digit PIN"
          value={pin}
          onChangeText={setPin}
        />

        {error ? (
          <Text style={styles.error} accessibilityRole="alert">
            {error}
          </Text>
        ) : null}

        <Pressable style={styles.button} onPress={onSubmit} disabled={isSubmitting}>
          {isSubmitting ? <ActivityIndicator color="#fff" /> : <Text style={styles.buttonText}>Log In</Text>}
        </Pressable>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  keyboardContainer: {
    flex: 1,
    backgroundColor: '#f3eee2'
  },
  container: {
    flexGrow: 1,
    justifyContent: 'center',
    padding: 24,
    paddingBottom: 36
  },
  title: {
    fontSize: 36,
    fontWeight: '800',
    marginBottom: 8,
    color: '#1f2937'
  },
  subtitle: {
    color: '#4b5563',
    marginBottom: 22,
    fontSize: 20,
    lineHeight: 27,
    fontWeight: '600'
  },
  input: {
    borderWidth: 1,
    borderColor: '#d1d5db',
    borderRadius: 12,
    paddingHorizontal: 14,
    paddingVertical: 14,
    marginBottom: 14,
    backgroundColor: '#fff',
    fontSize: 20,
    fontWeight: '600'
  },
  button: {
    marginTop: 6,
    backgroundColor: '#0f766e',
    paddingVertical: 15,
    borderRadius: 12,
    alignItems: 'center'
  },
  buttonText: {
    color: '#fff',
    fontWeight: '800',
    fontSize: 22
  },
  error: {
    marginTop: 12,
    color: '#991b1b',
    lineHeight: 26,
    fontSize: 18,
    fontWeight: '600'
  }
});
