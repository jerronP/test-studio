// frontend/src/AIAgentForm.jsx
import React, { useState } from 'react';

function AIAgentForm() {
    const [prompt, setPrompt] = useState('');
    const [result, setResult] = useState('');

    const handleSubmit = async (e) => {
        e.preventDefault();
        setResult('Loading...');

        try {
            const response = await fetch('/ai-agent/execute', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    prompt: prompt,
                    url: 'https://parabank.parasoft.com/parabank/index.htm',
                }),
            });

            if (!response.ok) {
                const errText = await response.text();
                setResult(`Error: ${response.status} - ${errText}`);
                return;
            }

            const data = await response.json();
            setResult(JSON.stringify(data, null, 2));
        } catch (error) {
            console.error('AI Agent request failed:', error);
            setResult('Error contacting AI Agent.');
        }
    };

    return (
        <div style={{ marginTop: '2rem' }}>
            <h2>🧠 AI Agent Prompt</h2>
            <form onSubmit={handleSubmit}>
        <textarea
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
            placeholder="e.g. login to parabank with username John and password Demo..."
            rows={4}
            style={{ width: '100%', fontSize: '14px' }}
        />
                <button type="submit" style={{ marginTop: '1rem' }}>Run AI Agent</button>
            </form>

            {result && (
                <div style={{ marginTop: '1rem' }}>
                    <h3>Result</h3>
                    <pre style={{ background: '#eee', padding: '1rem' }}>{result}</pre>
                </div>
            )}
        </div>
    );
}

export default AIAgentForm;
