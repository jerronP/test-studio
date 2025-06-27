import React, { useState } from 'react';
import './App.css';

function App() {
  const [recording, setRecording] = useState(false);
  const [url, setUrl] = useState('https://practicesoftwaretesting.com');
  const [cleanedFeature, setCleanedFeature] = useState('');

  const startRecording = async () => {
     setCleanedFeature('')
    if (!url) return;
    await fetch('/record/start', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ url }),
    });
    setRecording(true);
  };

  const stopRecording = async () => {
    await fetch('/record/stop', { method: 'POST' });
    setRecording(false);
  };

  const playback = async () => {
    await fetch('/record/playback', { method: 'POST' });
  };

  const generateFeature = async () => {
    setCleanedFeature('')
    const response = await fetch('/record/feature', { method: 'POST' });
    const rawText = await response.text();

    const cleanResponse = await fetch('/record/clean-feature', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ rawFeature: rawText })  ,
    });
    const cleanedText = await cleanResponse.text();
    setCleanedFeature(cleanedText);
  };

  return (
    <div style={{ padding: '2rem', maxWidth: '800px', margin: '0 auto' }}>
      <h1>Test Recorder</h1>
      <label>
        URL to record/playback:
        <input
          type="text"
          value={url}
          onChange={e => setUrl(e.target.value)}
          style={{ marginLeft: '1rem', width: '400px' }}
          disabled={recording}
        />
      </label>
      <div style={{ marginTop: '1rem' }}>
        <button onClick={recording ? stopRecording : startRecording}>
          {recording ? 'Stop Recording' : 'Start Recording'}
        </button>
        <button onClick={playback} style={{ marginLeft: '1rem' }}>
          Playback
        </button>
        <button onClick={generateFeature} style={{ marginLeft: '1rem' }}>
          Generate Feature File
        </button>
      </div>

      {cleanedFeature && (
        <div style={{ marginTop: '2rem' }}>
          <h2>Cleaned Feature File</h2>
          <textarea
            value={cleanedFeature}
            readOnly
            rows={14}
            style={{ width: '100%', fontFamily: 'monospace', fontSize: '14px' }}
          />
        </div>
      )}
    </div>
  );
}

export default App;
