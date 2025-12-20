import { useEffect, useState } from 'react';
import './ViewedQuotesScreen.css';
import quoteApi from '../api/quoteApi';
import { Quote } from '../types/Quote';
import { Toast } from './Toast';

interface ViewedQuotesScreenProps {
    onBack: () => void;
}

interface ViewedQuote extends Quote {
    isLiked: boolean;
}

export function ViewedQuotesScreen({ onBack }: ViewedQuotesScreenProps) {
    const [viewedQuotes, setViewedQuotes] = useState<ViewedQuote[]>([]);
    const [loading, setLoading] = useState<boolean>(true);
    const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

    useEffect(() => {
        loadViewedQuotes();
    }, []);

    const loadViewedQuotes = async () => {
        try {
            setLoading(true);
            const [viewedQuotes, likedQuotes] = await Promise.all([
                quoteApi.getViewedQuotes(),
                quoteApi.getLikedQuotes(),
            ]);

            const likedIds = new Set(likedQuotes.map(q => q.id));
            const viewedWithLikeStatus: ViewedQuote[] = viewedQuotes
                .map(q => ({
                    ...q,
                    isLiked: likedIds.has(q.id),
                }))
                .reverse();

            setViewedQuotes(viewedWithLikeStatus);
        } catch (error) {
            console.error('Failed to load viewed quotes:', error);
            showToast('Failed to load viewed quotes', 'error');
        } finally {
            setLoading(false);
        }
    };

    const showToast = (message: string, type: 'success' | 'error') => {
        setToast({ message, type });
    };

    const handleToggleLike = async (quoteId: number, isCurrentlyLiked: boolean) => {
        const previousViewedQuotes = [...viewedQuotes];

        const updatedViewedQuotes = viewedQuotes.map(q =>
            q.id === quoteId ? { ...q, isLiked: !isCurrentlyLiked } : q
        );
        setViewedQuotes(updatedViewedQuotes);

        try {
            if (isCurrentlyLiked) {
                await quoteApi.unlikeQuote(quoteId);
                showToast('Removed from favourites', 'success');
            } else {
                const quote = viewedQuotes.find(q => q.id === quoteId);
                if (quote) {
                    await quoteApi.likeQuote(quote);
                    showToast('Added to favourites', 'success');
                }
            }
        } catch (error) {
            console.error('Failed to update like:', error);
            setViewedQuotes(previousViewedQuotes);
            showToast('Failed to update like', 'error');
        }
    };

    return (
        <div className="viewed-quotes-screen">
            <div className="viewed-quotes-header">
                <button className="back-button" onClick={onBack}>
                    ← Back
                </button>
                <h2>Viewed Quotes</h2>
            </div>

            {loading ? (
                <div className="loading">Loading viewed quotes...</div>
            ) : viewedQuotes.length === 0 ? (
                <div className="empty-state">
                    No viewed quotes yet.
                </div>
            ) : (
                <div className="viewed-quotes-table-container">
                    <table className="viewed-quotes-table">
                        <thead>
                            <tr>
                                <th>Quote</th>
                                <th>Author</th>
                                <th>Favourite</th>
                            </tr>
                        </thead>
                        <tbody>
                            {viewedQuotes.map((quote) => (
                                <tr key={quote.id}>
                                    <td className="quote-cell">{quote.quoteText}</td>
                                    <td className="author-cell">{quote.author}</td>
                                    <td className="like-cell">
                                        <button
                                            className={`like-toggle-button ${quote.isLiked ? 'liked' : ''}`}
                                            onClick={() => handleToggleLike(quote.id, quote.isLiked)}
                                            title={quote.isLiked ? 'Remove from favourites' : 'Add to favourites'}
                                            aria-label={quote.isLiked ? 'Unlike' : 'Like'}
                                        >
                                            {quote.isLiked ? '❤️ Liked' : '🤍 Like'}
                                        </button>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}

            {toast && (
                <Toast
                    message={toast.message}
                    type={toast.type}
                    onClose={() => setToast(null)}
                />
            )}
        </div>
    );
}
