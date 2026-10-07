import './Reviews.css';
import {useCallback, useEffect, useRef, useState} from 'react';
import api from '../../api/axiosConfig';
import {useLocation, useParams} from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import {Button, Container, Row, Col} from 'react-bootstrap';
import ReviewForm from '../reviewForm/ReviewForm';
import { formatMeta } from '../../utils/movieFormat';
import NotFound from '../notFound/NotFound';
import ReviewDigest from '../reviewDigest/ReviewDigest';

const REVIEWS_PER_PAGE = 10;

const Reviews = ({getMovieData, movie, movieNotFound}) => {

    const revText = useRef();
    let params = useParams();
    const movieId = params.movieId;
    const { user, login } = useAuth();
    const location = useLocation();

    const [reviewError, setReviewError] = useState(null);
    // Reviews are paged by the API (newest first) and loaded separately from the movie
    const [reviews, setReviews] = useState([]);
    const [reviewPage, setReviewPage] = useState({ page: 0, totalPages: 0, totalItems: 0 });

    const loadReviews = useCallback(async (page) => {
        try {
            const response = await api.get(`/api/v1/movies/${movieId}/reviews`,
                { params: { page, size: REVIEWS_PER_PAGE } });
            const { items, totalPages, totalItems } = response.data;
            // Offset pages shift when reviews are added meanwhile, so skip any we already show
            setReviews((shown) => {
                if (page === 0) return items;
                const seen = new Set(shown.map((r) => r.id));
                return [...shown, ...items.filter((r) => !seen.has(r.id))];
            });
            setReviewPage({ page, totalPages, totalItems });
        } catch (err) {
            // A 404 means the movie doesn't exist; the movie request already shows "not found"
            if (err.response?.status !== 404) console.error(err);
        }
    }, [movieId]);

    useEffect(()=>{
        getMovieData(movieId);
        setReviews([]);
        loadReviews(0);
    },[movieId, loadReviews])

    const addReview = async (e) =>{
        e.preventDefault();

        const rev = revText.current;
        setReviewError(null);

        // Instant feedback without a network call. This is only for UX; the server checks again.
        if (!rev.value.trim()) {
            setReviewError("Write something before submitting.");
            return;
        }

        try
        {
            const response = await api.post("/api/v1/reviews",{reviewBody:rev.value,imdbId:movieId});

            // Newest first, like the API
            setReviews((shown) => [response.data, ...shown]);
            setReviewPage((p) => ({ ...p, totalItems: p.totalItems + 1 }));

            rev.value = "";
        }
        catch(err)
        {
            const problem = err.response?.data;
            if (err.response?.status === 401) {
                setReviewError("Your session expired. Log in again to write a review.");
            } else if (err.response?.status === 400 && problem?.errors) {
                setReviewError(Object.values(problem.errors).join(" "));
            } else if (err.response?.status === 429) {
                const wait = Number(err.response.headers['retry-after']);
                setReviewError(wait > 0
                    ? `You're posting too fast. Try again in ${wait} second${wait === 1 ? '' : 's'}.`
                    : "You're posting too fast. Try again in a moment.");
            } else {
                setReviewError("Couldn't save your review. Please try again.");
                console.error(err);
            }
        }
    }

    // UX only: decides whether to SHOW the button. The server makes the real decision.
    // Compares the stable Keycloak id, like the server does, never the (changeable) username.
    const canDelete = (review) =>
        user && (user.id === review.authorId || user.roles?.includes('ADMIN'));

    const removeFromList = (review) => {
        setReviews((shown) => shown.filter((r) => r.id !== review.id));
        setReviewPage((p) => ({ ...p, totalItems: Math.max(0, p.totalItems - 1) }));
    };

    const deleteReview = async (review) => {
        if (!window.confirm('Delete this review?')) return;
        setReviewError(null);
        try {
            await api.delete(`/api/v1/reviews/${review.id}`);
            removeFromList(review);
        } catch (err) {
            if (err.response?.status === 403) {
                setReviewError("You can only delete your own reviews.");
            } else if (err.response?.status === 404) {
                // Already gone (e.g. deleted in another tab): just drop it from the list
                removeFromList(review);
            } else {
                setReviewError("Couldn't delete the review. Please try again.");
                console.error(err);
            }
        }
    };

    const cast = movie?.cast ?? [];
    const audienceReviews = movie?.audienceReviews ?? [];
    if (movieNotFound) {
        return <NotFound/>;
    }

  return (
    <Container className="reviews-page">
        <Row>
            <Col>
                <h3 className="mb-1">{movie?.title ?? "Reviews"}</h3>
                <p className="reviews-meta">
                    {[formatMeta(movie), movie?.genres?.join(", ")].filter(Boolean).join(" · ")}
                </p>
            </Col>
        </Row>
        <Row className="mt-2">
            <Col md={4} className="mb-4">
                {movie?.poster && <img className="reviews-poster" src={movie.poster} alt={`${movie.title} poster`} />}
            </Col>
            <Col md={8}>
                {movie?.overview && (
                    <section className="reviews-section">
                        <h5>Synopsis</h5>
                        <p>{movie.overview}</p>
                    </section>
                )}

                {cast.length > 0 && (
                    <section className="reviews-section">
                        <h5>Top cast</h5>
                        <div className="cast-list">
                            {cast.map((c) => (
                                <div className="cast-member" key={c.name}>
                                    {c.profileUrl
                                        ? <img src={c.profileUrl} alt={c.name} />
                                        : <div className="cast-photo-placeholder">{c.name.charAt(0)}</div>}
                                    <div>
                                        <div className="cast-name">{c.name}</div>
                                        {c.character && <div className="cast-character">as {c.character}</div>}
                                    </div>
                                </div>
                            ))}
                        </div>
                    </section>
                )}

                <ReviewDigest movieId={movieId} />

                <section className="reviews-section">
                    <h5>What viewers are saying</h5>
                    {audienceReviews.length === 0 && <p className="reviews-empty">No viewer reviews on TMDB yet.</p>}
                    {audienceReviews.map((r) => (
                        <article className="audience-review" key={r.url ?? r.author}>
                            <div className="audience-review-header">
                                <strong>{r.author}</strong>
                                {r.rating != null && <span className="audience-review-rating">★ {r.rating}/10</span>}
                            </div>
                            <p>{r.content}</p>
                            {r.url && (
                                // rel="noopener noreferrer" stops the new tab from getting a handle back to this page
                                <a href={r.url} target="_blank" rel="noopener noreferrer">Read the full review on TMDB</a>
                            )}
                        </article>
                    ))}
                </section>

                <section className="reviews-section">
                    <h5>Your reviews{reviewPage.totalItems > 0 && ` (${reviewPage.totalItems})`}</h5>
                    {user ? (
                        <ReviewForm handleSubmit={addReview} revText={revText} labelText = "Write a Review?" />
                    ) : (
                        <p>
                            <Button variant="link" className="p-0 align-baseline" onClick={() => login(location.pathname)}>Log in</Button>
                            {' '}to write a review.
                        </p>
                    )}
                    {reviewError && <p className="text-danger mt-2 mb-0">{reviewError}</p>}
                    <hr />
                    {reviews.map((r) => (
                        <div key={r.id} className="app-review">
                            <div className="app-review-header">
                                <strong>{r.authorName ?? 'Early reviewer'}</strong>
                                {r.createdAt && <span className="app-review-date">{new Date(r.createdAt).toLocaleDateString()}</span>}
                                {canDelete(r) && (
                                    <Button variant="link" size="sm" className="text-danger p-0 ms-auto" onClick={() => deleteReview(r)}>
                                        Delete
                                    </Button>
                                )}
                            </div>
                            <p className="mb-0">{r.body}</p>
                            <hr />
                        </div>
                    ))}
                    {reviewPage.page + 1 < reviewPage.totalPages && (
                        <Button variant="outline-info" size="sm" onClick={() => loadReviews(reviewPage.page + 1)}>
                            Load more reviews
                        </Button>
                    )}
                </section>
            </Col>
        </Row>
    </Container>
  )
}

export default Reviews
