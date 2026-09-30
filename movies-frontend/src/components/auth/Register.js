import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Button, Container, Form } from 'react-bootstrap';
import { useAuth } from '../../auth/AuthContext';

const Register = () => {
    const { register } = useAuth();
    const navigate = useNavigate();
    const location = useLocation();

    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [errors, setErrors] = useState({});
    const [submitting, setSubmitting] = useState(false);

    const handleSubmit = async (e) => {
        e.preventDefault();
        setErrors({});
        setSubmitting(true);
        try {
            await register(username, password);
            navigate(location.state?.from ?? '/', { replace: true });
        } catch (err) {
            const problem = err.response?.data;
            if (err.response?.status === 400 && problem?.errors) {
                setErrors(problem.errors);
            } else if (err.response?.status === 409) {
                setErrors({ username: problem.detail });
            } else {
                setErrors({ form: "Couldn't create your account. Please try again." });
            }
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <Container className="auth-page">
            <h3>Create an account</h3>
            <Form onSubmit={handleSubmit} noValidate>
                <Form.Group className="mb-3" controlId="registerUsername">
                    <Form.Label>Username</Form.Label>
                    <Form.Control value={username} onChange={(e) => setUsername(e.target.value)}
                                  autoComplete="username" isInvalid={!!errors.username} />
                    <Form.Control.Feedback type="invalid">{errors.username}</Form.Control.Feedback>
                </Form.Group>
                <Form.Group className="mb-3" controlId="registerPassword">
                    <Form.Label>Password</Form.Label>
                    <Form.Control type="password" value={password} onChange={(e) => setPassword(e.target.value)}
                                  autoComplete="new-password" maxLength={64} isInvalid={!!errors.password} />
                    <Form.Text className="text-secondary">At least 15 characters. A few random words make a strong, memorable passphrase.</Form.Text>
                    <Form.Control.Feedback type="invalid">{errors.password}</Form.Control.Feedback>
                </Form.Group>
                {errors.form && <p className="text-danger">{errors.form}</p>}
                <Button type="submit" variant="outline-info" disabled={submitting}>Register</Button>
            </Form>
            <p className="mt-3">Already have an account? <Link to="/login" state={location.state}>Log in</Link></p>
        </Container>
    );
};

export default Register;