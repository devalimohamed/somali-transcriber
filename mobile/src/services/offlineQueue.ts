import AsyncStorage from '@react-native-async-storage/async-storage';
import { PendingUpload } from '../types';

const QUEUE_KEY = 'somtranscriber_pending_uploads';

export async function getPendingUploads(): Promise<PendingUpload[]> {
  const raw = await AsyncStorage.getItem(QUEUE_KEY);
  if (!raw) {
    return [];
  }
  return JSON.parse(raw);
}

export async function savePendingUploads(items: PendingUpload[]): Promise<void> {
  await AsyncStorage.setItem(QUEUE_KEY, JSON.stringify(items));
}

export async function enqueueUpload(item: PendingUpload): Promise<void> {
  const current = await getPendingUploads();
  current.push(item);
  await savePendingUploads(current);
}

export async function removeUpload(id: string): Promise<void> {
  const current = await getPendingUploads();
  const updated = current.filter((item) => item.id !== id);
  await savePendingUploads(updated);
}
