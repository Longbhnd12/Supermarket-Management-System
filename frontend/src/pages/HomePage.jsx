import { useEffect, useState } from 'react';
import API from '../services/api';

export default function HomePage() {

    const [backendStatus, setBackendStatus] = useState('Checking...');

    useEffect(() => {
        const checkBackend = async () => {
            try {
                const response = await API.get('/health');
                setBackendStatus(response.data);
            } catch (error) {
                console.error(error);
                setBackendStatus('Backend connection failed');
            }
        };

        checkBackend();
    }, []);

    return (
        <div>
            <h1>Welcome to SMS</h1>

            <p>Frontend is running successfully.</p>

            <h3>Backend Connection:</h3>
            <p>{backendStatus}</p>
        </div>
    );
}