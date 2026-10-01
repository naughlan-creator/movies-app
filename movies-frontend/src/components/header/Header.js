import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faVideoSlash } from "@fortawesome/free-solid-svg-icons";
import Button from "react-bootstrap/Button";
import Container from "react-bootstrap/Container"
import Nav from "react-bootstrap/Nav";
import Navbar from "react-bootstrap/Navbar";
import {NavLink, useLocation} from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";

const Header = () => {
    const { user, ready, login, register, logout } = useAuth();
    const location = useLocation();

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
