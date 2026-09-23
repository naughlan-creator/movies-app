import {useParams} from 'react-router-dom';
import ReactPlayer from 'react-player';
import './Trailer.css';

const Trailer = () => {

    let params = useParams();
    let key = params.ytTrailerId;

  return (
    <div className="react-player-container">
      {key? (
        <ReactPlayer  controls
                      src={`https://www.youtube.com/watch?v=${key}`}
                      width="100%"
                      height="100%"
        />) : null}
    </div>
  );
}

export default Trailer