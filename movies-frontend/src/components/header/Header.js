import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faVideoSlash } from "@fortawesome/free-solid-svg-icons";
import Button from "react-bootstrap/Button";
import Container from "react-bootstrap/Container"
import Nav from "react-bootstrap/Nav";
import Navbar from "react-bootstrap/Navbar";
import {Link, NavLink, useLocation, useNavigate} from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";

const Header = () => {
    const { user, ready, logout } = useAuth();
    const navigate = useNavigate();
    const location = useLocation();

    const handleLogout = () => {
        logout();
        navigate('/');
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
                {ready && (user ? (
                    <>
                        <Navbar.Text className="me-3">Signed in as <strong>{user.username}</strong></Navbar.Text>
                        <Button variant="outline-info" onClick={handleLogout}>Log out</Button>
                    </>
                ) : (
                    <>
                        <Button as={Link} to="/login" state={{ from: location.pathname }} variant="outline-info" className="me-2">Login</Button>
                        <Button as={Link} to="/register" state={{ from: location.pathname }} variant="outline-info">Register</Button>
                    </>
                ))}
            </Navbar.Collapse>
        </Container>
    </Navbar>
  )
}

export default Header