export interface SinchClientConfig {
  appKey: string;
  environmentHost: string;
  userId: string;
}

export type RegistrationCredentialsProvider = () => Promise<string>;

export class SinchRegistrationError extends Error {}
