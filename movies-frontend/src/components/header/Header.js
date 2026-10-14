import { useState } from "react";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faVideoSlash } from "@fortawesome/free-solid-svg-icons";
import Button from "react-bootstrap/Button";
import Container from "react-bootstrap/Container";
import Form from "react-bootstrap/Form";
import Nav from "react-bootstrap/Nav";
import Navbar from "react-bootstrap/Navbar";
import { NavLink, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";

const Header = () => {
    const { user, ready, login, register, logout } = useAuth();
    const location = useLocation();
    const navigate = useNavigate();
    const [query, setQuery] = useState("");

    // Search when the visitor presses Enter, not on every keystroke: each search is a call to the GPU
    const submitSearch = (e) => {
        e.preventDefault();
        const q = query.trim();
        if (q) navigate(`/search?q=${encodeURIComponent(q)}`);
    };

return (
    <Navbar bg="dark" variant="dark" expand="lg">
        <Container fluid>
            <Navbar.Brand href="/" style={{"color":'gold'}}>
                <FontAwesomeIcon icon ={faVideoSlash}/>Gold
            </Navbar.Brand>
            <Navbar.Toggle aria-controls="navbarScroll" />
            <Navbar.Collapse id="navbarScroll">
                    <Nav
                        className="me-auto my-2 my-lg-0"
                        style={{maxHeight: '100px'}}
                        navbarScroll
                    >
                    <NavLink className ="nav-link" to="/">Home</NavLink>
                    <NavLink className ="nav-link" to="/watchList">Watch List</NavLink>
                </Nav>

                <Form className="d-flex me-3 my-2 my-lg-0" role="search" onSubmit={submitSearch}>
                    <Form.Control
                        type="search"
                        placeholder="Describe a movie you'd like..."
                        aria-label="Search movies by description"
                        maxLength={200}
                        value={query}
                        onChange={(e) => setQuery(e.target.value)}
                    />
                </Form>

                {ready && (user ? (
                    <>
                        <Navbar.Text className="me-3">Signed in as <strong>{user.username}</strong></Navbar.Text>
                        <Button variant="outline-info" onClick={logout}>Log out</Button>
                    </>
                ) : (
                    <>
                        <Button variant="outline-info" className="me-2" onClick={() => login(location.pathname)}>Login</Button>
                        <Button variant="outline-info" onClick={() => register(location.pathname)}>Register</Button>
                    </>
                ))}
            </Navbar.Collapse>
        </Container>
    </Navbar>
  )
}

export default Header
