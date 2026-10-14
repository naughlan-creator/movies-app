import './SearchResults.css';
import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Container } from 'react-bootstrap';
import api from '../../api/axiosConfig';
import { formatMeta } from '../../utils/movieFormat';

const SearchResults = () => {
    const [params] = useSearchParams();
    const query = params.get('q')?.trim() ?? '';
    // status: loading | done | unavailable (search is off or down) | error
    const [state, setState] = useState({ status: 'loading', hits: [] });

    useEffect(() => {
        if (!query) {
            setState({ status: 'done', hits: [] });
            return;
        }
        // A new search cancels the previous one, so a slow old answer can't replace a newer one
        const controller = new AbortController();
        setState({ status: 'loading', hits: [] });
        api.get('/api/v1/movies/search', { params: { q: query, limit: 12 }, signal: controller.signal })
            .then((response) => setState({ status: 'done', hits: response.data }))
            .catch((err) => {
                if (controller.signal.aborted) return;
                const unavailable = err.response?.status === 503;
                if (!unavailable) console.error(err);
                setState({ status: unavailable ? 'unavailable' : 'error', hits: [] });
            });
        return () => controller.abort();
    }, [query]);

    return (
        <Container className="search-page">
            <h3 className="mb-1">{query ? `Movies like "${query}"` : 'Search'}</h3>
            <p className="search-hint">Ranked by meaning, not keywords: describe a mood, a story or a feeling.</p>

            {state.status === 'loading' && <p className="search-hint">Searching...</p>}
            {state.status === 'unavailable' && <p>Search isn't available right now. Please try again later.</p>}
            {state.status === 'error' && <p className="text-danger">Something went wrong. Please try again.</p>}
            {state.status === 'done' && query && state.hits.length === 0 && <p>No movies found.</p>}

            {/* No score on screen: similarity scores only mean something relative to each other */}
            <div className="search-results">
                {state.hits.map(({ movie }) => (
                    <Link to={`/Reviews/${movie.imdbId}`} className="search-result" key={movie.imdbId}>
                        {movie.poster
                            ? <img src={movie.poster} alt="" />
                            : <div className="search-poster-placeholder" />}
                        <div className="search-result-text">
                            <div className="search-title">{movie.title}</div>
                            <div className="search-meta">{formatMeta(movie)}</div>
                            {movie.overview && <p className="search-overview">{movie.overview}</p>}
                        </div>
                    </Link>
                ))}
            </div>
        </Container>
    );
};

export default SearchResults;
