import './Reviews.css';
import {useEffect, useRef} from 'react';
import api from '../../api/axiosConfig';
import {useParams} from 'react-router-dom';
import {Container, Row, Col} from 'react-bootstrap';
import ReviewForm from '../reviewForm/ReviewForm';
import { formatMeta } from '../../utils/movieFormat';
import NotFound from '../notFound/NotFound';

const Reviews = ({getMovieData,movie,reviews,setReviews, movieNotFound}) => {

    const revText = useRef();
    let params = useParams();
    const movieId = params.movieId;

    useEffect(()=>{
        getMovieData(movieId);
    },[movieId])

    const addReview = async (e) =>{
        e.preventDefault();

        const rev = revText.current;

        try
        {
            const response = await api.post("/api/v1/reviews",{reviewBody:rev.value,imdbId:movieId});

            const updatedReviews = [...reviews, response.data];

            rev.value = "";

            setReviews(updatedReviews);
        }
        catch(err)
        {
            console.error(err);
        }
    }

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
                    <h5>Your reviews</h5>
                    <ReviewForm handleSubmit={addReview} revText={revText} labelText = "Write a Review?" />
                    <hr />
                    {reviews.map((r) => (
                        <div key={r.id}>
                            <p className="mb-0">{r.body}</p>
                            <hr />
                        </div>
                    ))}
                </section>
            </Col>
        </Row>
    </Container>
  )
}

export default Reviews
