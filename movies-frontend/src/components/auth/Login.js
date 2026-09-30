import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Button, Container, Form } from 'react-bootstrap';
import { useAuth } from '../../auth/AuthContext';

const Login = () => {
    const { login } = useAuth();
    const navigate = useNavigate();
    const location = useLocation();

    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState(null);
    const [submitting, setSubmitting] = useState(false);

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError(null);
        setSubmitting(true);
        try {
            await login(username, password);
            // Back to where the user came from (e.g. a movie's reviews), or home
            navigate(location.state?.from ?? '/', { replace: true });
        } catch (err) {
            setError(err.response?.status === 401
                ? 'Wrong username or password.'
                : "Couldn't log in. Please try again.");
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <Container className="auth-page">
            <h3>Log in</h3>
            <Form onSubmit={handleSubmit}>
                <Form.Group className="mb-3" controlId="loginUsername">
                    <Form.Label>Username</Form.Label>
                    <Form.Control value={username} onChange={(e) => setUsername(e.target.value)}
                                  autoComplete="username" required />
                </Form.Group>
                <Form.Group className="mb-3" controlId="loginPassword">
                    <Form.Label>Password</Form.Label>
                    <Form.Control type="password" value={password} onChange={(e) => setPassword(e.target.value)}
                                  autoComplete="current-password" required />
                </Form.Group>
                {error && <p className="text-danger">{error}</p>}
                <Button type="submit" variant="outline-info" disabled={submitting}>Log in</Button>
            </Form>
            <p className="mt-3">No account? <Link to="/register" state={location.state}>Register</Link></p>
        </Container>
    );
};

export default Login;