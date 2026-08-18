import { useEffect, useState } from 'react';
import { Button, StyleSheet, Text, TextInput, View } from 'react-native';
import { SinchCalling } from 'react-native-sinch-calling';

// TODO: point this at your own backend endpoint that mints a Sinch
// registration JWT signed with your Application Secret.
async function fetchRegistrationJwt(): Promise<string> {
  throw new Error('fetchRegistrationJwt is not implemented yet');
}

export default function App() {
  const [appKey, setAppKey] = useState('');
  const [environmentHost, setEnvironmentHost] = useState('ocra.api.sinch.com');
  const [userId, setUserId] = useState('');
  const [calleeId, setCalleeId] = useState('');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [callerId, setCallerId] = useState('');
  const [conferenceId, setConferenceId] = useState('');
  const [status, setStatus] = useState('idle');
  const [callId, setCallId] = useState<string | null>(null);
  const [callState, setCallState] = useState('no call');
  const [muted, setMuted] = useState(false);
  const [speakerEnabled, setSpeakerEnabled] = useState(false);

  useEffect(() => {
    SinchCalling.setRegistrationCredentialsProvider(fetchRegistrationJwt);

    const subscriptions = [
      SinchCalling.onClientStarted(() => setStatus('started')),
      SinchCalling.onClientStartFailed(({ message, code }) =>
        setStatus(`failed: ${message} (code ${code})`)
      ),
      SinchCalling.onIncomingCall((event) => {
        setCallId(event.callId);
        setCallState(`incoming from ${event.remoteUserId}`);
      }),
      SinchCalling.onCallProgressing(() => setCallState('progressing')),
      SinchCalling.onCallEstablished(() => setCallState('established')),
      SinchCalling.onCallEnded(({ endCause }) => {
        setCallState(`ended (${endCause})`);
        setCallId(null);
      }),

      // Custom incoming-call flow demo — see the README's "Custom
      // incoming-call flows" section. There's no real backend here, so
      // `onReportTestIncomingCall` below simulates the push arriving.
      SinchCalling.onIncomingCallUIShown(
        ({ callId: shownCallId, displayName }) => {
          setCallId(shownCallId);
          setCallState(`custom call UI shown: ${displayName}`);
        }
      ),
      SinchCalling.onCallUIAnswered(({ callId: answeredCallId }) => {
        if (!conferenceId) {
          setCallState('answered, but no conference id set to resolve into');
          return;
        }
        const realCallId = SinchCalling.resolveCallUIToConference(
          answeredCallId,
          conferenceId,
          callerId
        );
        if (realCallId) {
          setCallId(realCallId);
          setCallState('resolved into conference call');
        } else {
          setCallState('failed to resolve into a conference call');
          SinchCalling.dismissCallUI(answeredCallId);
        }
      }),
      SinchCalling.onCallUIDeclined(() => {
        setCallState('custom call UI declined');
        setCallId(null);
      }),
    ];

    return () => {
      subscriptions.forEach((subscription) => subscription.remove());
    };
  }, [callerId, conferenceId]);

  const onLogin = () => {
    setStatus('starting...');
    SinchCalling.configure({ appKey, environmentHost, userId });
    SinchCalling.start();
  };

  const onCall = () => {
    const newCallId = SinchCalling.callUser(calleeId);
    setCallId(newCallId);
    setCallState('calling...');
  };

  const onCallPhoneNumber = () => {
    const newCallId = SinchCalling.callPhoneNumber(phoneNumber, callerId);
    setCallId(newCallId);
    setCallState('calling phone number...');
  };

  const onReportTestIncomingCall = () => {
    // Simulates your backend's custom push arriving — see
    // `SinchCalling.configureCustomIncomingCallPush` in the README for how
    // this would normally be auto-detected from a real push payload.
    SinchCalling.reportIncomingCallUI('example-demo-call', 'Demo Caller');
  };

  const onAnswer = () => callId && SinchCalling.answerCall(callId);
  const onHangup = () => callId && SinchCalling.hangupCall(callId);

  const onToggleMute = () => {
    const next = !muted;
    SinchCalling.setMuted(next);
    setMuted(next);
  };

  const onToggleSpeaker = () => {
    const next = !speakerEnabled;
    SinchCalling.setSpeakerEnabled(next);
    setSpeakerEnabled(next);
  };

  return (
    <View style={styles.container}>
      <TextInput
        style={styles.input}
        placeholder="Application key"
        value={appKey}
        onChangeText={setAppKey}
      />
      <TextInput
        style={styles.input}
        placeholder="Environment host"
        value={environmentHost}
        onChangeText={setEnvironmentHost}
      />
      <TextInput
        style={styles.input}
        placeholder="User id"
        value={userId}
        onChangeText={setUserId}
      />
      <Button title="Login" onPress={onLogin} />
      <Text style={styles.status}>Status: {status}</Text>

      <TextInput
        style={styles.input}
        placeholder="Callee user id"
        value={calleeId}
        onChangeText={setCalleeId}
      />
      <Button title="Call" onPress={onCall} />

      <TextInput
        style={styles.input}
        placeholder="Phone number (E.164, e.g. +14155550101)"
        value={phoneNumber}
        onChangeText={setPhoneNumber}
      />
      <TextInput
        style={styles.input}
        placeholder="Caller id (Android only, e.g. +14155550100)"
        value={callerId}
        onChangeText={setCallerId}
      />
      <Button title="Call phone number" onPress={onCallPhoneNumber} />

      <TextInput
        style={styles.input}
        placeholder="Conference id (for custom incoming-call demo)"
        value={conferenceId}
        onChangeText={setConferenceId}
      />
      <Button
        title="Simulate custom incoming call"
        onPress={onReportTestIncomingCall}
      />

      <Button title="Answer" onPress={onAnswer} disabled={!callId} />
      <Button title="Hang up" onPress={onHangup} disabled={!callId} />
      <Button
        title={muted ? 'Unmute' : 'Mute'}
        onPress={onToggleMute}
        disabled={!callId}
      />
      <Button
        title={speakerEnabled ? 'Disable speaker' : 'Enable speaker'}
        onPress={onToggleSpeaker}
        disabled={!callId}
      />
      <Text style={styles.status}>Call: {callState}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
    padding: 24,
  },
  input: {
    width: '100%',
    borderWidth: 1,
    borderColor: '#ccc',
    borderRadius: 8,
    padding: 8,
  },
  status: {
    marginTop: 12,
  },
});
