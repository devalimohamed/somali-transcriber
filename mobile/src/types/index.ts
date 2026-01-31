export type AuthTokens = {
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
};

export type CallStatus =
  | 'CREATED'
  | 'UPLOADED'
  | 'TRANSCRIBING'
  | 'FORMATTING'
  | 'READY'
  | 'READY_WITH_WARNING'
  | 'FAILED'
  | 'FINALIZED';

export type CallResponse = {
  callId: string;
  status: CallStatus;
  noteText: string | null;
  warning: string | null;
  metadata: {
    callAt: string;
    userId: string;
  };
  isFinal: boolean;
  createdAt: string;
  updatedAt: string;
};

export type PendingUpload = {
  id: string;
  callId: string;
  encryptedAudio: string;
  mimeType: string;
  durationSeconds: number;
  extension: string;
};
