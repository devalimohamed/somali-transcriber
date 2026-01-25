import React, { useMemo, useState } from 'react';
import { ActivityIndicator, Pressable, SafeAreaView, StyleSheet, Text, View } from 'react-native';
import { AuthProvider, useAuth } from './src/context/AuthContext';
import { LoginScreen } from './src/screens/LoginScreen';
import { RecorderScreen } from './src/screens/RecorderScreen';
import { HistoryScreen } from './src/screens/HistoryScreen';

type Tab = 'record' | 'history';

function MainApp() {
  const { tokens, logout, isLoading } = useAuth();
  const [tab, setTab] = useState<Tab>('record');

  const tabView = useMemo(() => {
    if (tab === 'history') {
      return <HistoryScreen />;
    }
    return <RecorderScreen />;
  }, [tab]);

  if (isLoading) {
    return (
      <SafeAreaView style={styles.loadingContainer}>
        <ActivityIndicator size="large" />
      </SafeAreaView>
    );
  }

  if (!tokens) {
    return <LoginScreen />;
  }

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.topBar}>
        <View style={styles.tabs}>
          <Pressable
            style={[styles.tabButton, tab === 'record' ? styles.activeTab : null]}
            onPress={() => setTab('record')}
          >
            <Text style={tab === 'record' ? styles.activeTabText : styles.tabText}>Record</Text>
          </Pressable>
          <Pressable
            style={[styles.tabButton, tab === 'history' ? styles.activeTab : null]}
            onPress={() => setTab('history')}
          >
            <Text style={tab === 'history' ? styles.activeTabText : styles.tabText}>History</Text>
          </Pressable>
        </View>

        <Pressable style={styles.logoutButton} onPress={() => void logout()}>
          <Text style={styles.logoutText}>Logout</Text>
        </Pressable>
      </View>

      {tabView}
    </SafeAreaView>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <MainApp />
    </AuthProvider>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f8f4ea'
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center'
  },
  topBar: {
    paddingHorizontal: 14,
    paddingTop: 8,
    paddingBottom: 8,
    borderBottomWidth: 1,
    borderBottomColor: '#e5e7eb',
    backgroundColor: '#fff',
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center'
  },
  tabs: {
    flexDirection: 'row',
    gap: 8
  },
  tabButton: {
    borderWidth: 1,
    borderColor: '#cbd5e1',
    borderRadius: 10,
    paddingHorizontal: 16,
    paddingVertical: 8
  },
  activeTab: {
    backgroundColor: '#0f766e',
    borderColor: '#0f766e'
  },
  tabText: {
    color: '#1f2937',
    fontWeight: '700',
    fontSize: 18
  },
  activeTabText: {
    color: '#fff',
    fontWeight: '800',
    fontSize: 18
  },
  logoutButton: {
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: '#be123c'
  },
  logoutText: {
    color: '#be123c',
    fontWeight: '800',
    fontSize: 17
  }
});
