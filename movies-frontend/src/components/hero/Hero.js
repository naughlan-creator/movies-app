import './Hero.css';
import Carousel from 'react-material-ui-carousel';
import { Paper } from '@mui/material';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faCirclePlay } from '@fortawesome/free-solid-svg-icons';
import {Link, useNavigate} from "react-router-dom";
import Button from 'react-bootstrap/Button';
import { formatMeta } from '../../utils/movieFormat';


const Hero = ({movies}) => {

    const navigate = useNavigate();

    function reviews(movieId)
    {
        navigate(`/Reviews/${movieId}`);
    }

  return (
    <div className ='movie-carousel-container'>
      <Carousel>
        {
            movies?.map((movie) =>{
                const cast = movie.cast ?? [];
                const hasHoverInfo = movie.overview || cast.length > 0;
                return(
                    <Paper key={movie.imdbId}>
                        <div className = 'movie-card-container'>
                            <div className="movie-card" style={{"--img": `url(${movie.backdrops?.[0] ?? ''})`}}>
                                <div className="movie-detail">
                                    {/* tabIndex makes the poster focusable, so the overlay also opens on tap or keyboard focus */}
                                    <div className="movie-poster" tabIndex={hasHoverInfo ? 0 : undefined}>
                                        <img src={movie.poster} alt={`${movie.title} poster`} />
                                        {hasHoverInfo && (
                                            <div className="movie-poster-overlay">
                                                {movie.overview && <p className="movie-synopsis">{movie.overview}</p>}
                                                {cast.length > 0 && (
                                                    <p className="movie-starring">
                                                        <span>Starring</span>
                                                        {cast.map(c => c.name).join(' · ')}
                                                    </p>
                                                )}
                                            </div>
                                        )}
                                    </div>
                                    <div className="movie-title">
                                        {movie.trendingRank && <span className="movie-trending-badge">#{movie.trendingRank} trending this week</span>}
                                        <h4>{movie.title}</h4>
                                        <span className="movie-meta">{formatMeta(movie)}</span>
                                    </div>
                                    <div className="movie-buttons-container">
                                        {movie.trailerLink ? (
                                            <Link to={`/Trailer/${movie.trailerLink.substring(movie.trailerLink.length - 11)}`}>
                                                <div className="play-button-icon-container">
                                                    <FontAwesomeIcon className="play-button-icon"
                                                        icon = {faCirclePlay}
                                                    />
                                                </div>
                                            </Link>
                                        ) : <div className="play-button-icon-container" />}

                                        <div className="movie-review-button-container">
                                            <Button variant ="info" onClick={() => reviews(movie.imdbId)} >Reviews</Button>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </Paper>
                )
            })
        }
      </Carousel>
    </div>
  )
}

export default Hero
