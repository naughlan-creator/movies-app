import './ReviewDigest.css';
import { useEffect, useState } from 'react';
import api from '../../api/axiosConfig';

const SENTIMENT_LABELS = {
    POSITIVE: 'Mostly positive',
    MIXED: 'Mixed',
    NEGATIVE: 'Mostly negative',
    UNKNOWN: 'Too early to tell',
};

// "5 minutes ago", "yesterday"... in the visitor's own language
const timeAgo = (iso) => {
    const seconds = Math.round((new Date(iso) - Date.now()) / 1000);
    const format = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });
    for (const [unit, size] of [['day', 86400], ['hour', 3600], ['minute', 60]]) {
        if (Math.abs(seconds) >= size) return format.format(Math.round(seconds / size), unit);
    }
    return format.format(0, 'minute'); // "this minute"
};

const ReviewDigest = ({ movieId }) => {
    // undefined = loading, null = no digest (or it failed to load), object = the digest
    const [digest, setDigest] = useState(undefined);

    useEffect(() => {
        // If the visitor switches movies before this answer arrives, cancel it:
        // otherwise movie A's digest could appear on movie B's page
        const controller = new AbortController();
        setDigest(undefined);
        api.get(`/api/v1/movies/${movieId}/digest`, { signal: controller.signal })
            .then((response) => setDigest(response.data))
            .catch((err) => {
                if (controller.signal.aborted) return;
                // 404 just means "not generated yet". For anything else, hide the section: the page works without it
                if (err.response?.status !== 404) console.error(err);
                setDigest(null);
            });
        return () => controller.abort();
    }, [movieId]);

    if (!digest) return null;

    const { verdict, liked, disliked, watchIf, sentiment } = digest.digest;

    return (
        <section className="reviews-section review-digest">
            <div className="review-digest-header">
                <h5>The verdict</h5>
                <span className={`digest-sentiment digest-sentiment-${sentiment.toLowerCase()}`}>
                    {SENTIMENT_LABELS[sentiment] ?? sentiment}
                </span>
            </div>

            {/* Plain {text}: React escapes it, so model output can never become HTML or script */}
            <p className="digest-verdict">{verdict}</p>

            {(liked.length > 0 || disliked.length > 0) && (
                <div className="digest-columns">
                    {liked.length > 0 && (
                        <div>
                            <h6>Liked</h6>
                            <ul>{liked.map((item, i) => <li key={`${i}-${item}`}>{item}</li>)}</ul>
                        </div>
                    )}
                    {disliked.length > 0 && (
                        <div>
                            <h6>Disliked</h6>
                            <ul>{disliked.map((item, i) => <li key={`${i}-${item}`}>{item}</li>)}</ul>
                        </div>
                    )}
                </div>
            )}

            {watchIf && <p className="digest-watch-if"><strong>Watch it if:</strong> {watchIf}</p>}

            <p className="digest-footnote">
                AI summary of {digest.reviewsUsed} reviews · updated {timeAgo(digest.generatedAt)}
                {digest.refreshing && ' · new reviews are being summarized'}
                {' · It can make mistakes.'}
            </p>
        </section>
    );
};

export default ReviewDigest;