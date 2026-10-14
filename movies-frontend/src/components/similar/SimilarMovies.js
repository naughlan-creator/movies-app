import './SimilarMovies.css';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../../api/axiosConfig';

const SimilarMovies = ({ movieId }) => {
    const [hits, setHits] = useState([]);

    useEffect(() => {
        const controller = new AbortController();
        setHits([]);
        api.get(`/api/v1/movies/${movieId}/similar`, { params: { limit: 6 }, signal: controller.signal })
            .then((response) => setHits(response.data))
            .catch((err) => {
                if (controller.signal.aborted) return;
                // 503 = search is off or down, 404 = unknown movie: either way the page works without this row
                if (![404, 503].includes(err.response?.status)) console.error(err);
            });
        return () => controller.abort();
    }, [movieId]);

    if (hits.length === 0) return null;

    return (
        <section className="reviews-section">
            <h5>More like this</h5>
            <div className="similar-row">
                {hits.map(({ movie }) => (
                    <Link to={`/Reviews/${movie.imdbId}`} className="similar-movie" key={movie.imdbId}>
                        {movie.poster
                            ? <img src={movie.poster} alt="" />
                            : <div className="similar-poster-placeholder" />}
                        <span>{movie.title}</span>
                    </Link>
                ))}
            </div>
        </section>
    );
};

export default SimilarMovies;
