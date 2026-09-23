import { Outlet } from "react-router-dom";

const Layout = () => {
    return (
        <>
            <main>
                <Outlet/>
            </main>
            {/* TMDB's API terms require this attribution */}
            <footer className="app-footer">
                Movie data from TMDB. This product uses the TMDB API but is not endorsed or certified by TMDB.
            </footer>
        </>
    )
}

export default Layout
