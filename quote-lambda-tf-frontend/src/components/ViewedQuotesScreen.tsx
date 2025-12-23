import { useEffect, useState } from 'react';
import './ViewedQuotesScreen.css';
import quoteApi from '../api/quoteApi';
import { Quote } from '../types/Quote';
import { Toast } from './Toast';
import { BackendRestartNotification, useBackendRestartNotification } from './BackendRestartNotification';

interface ViewedQuotesScreenProps {
    onBack: () => void;
    onDeleteAll?: () => void;
}

interface ViewedQuote extends Quote {
    isLiked: boolean;
}

export function ViewedQuotesScreen({ onBack, onDeleteAll }: ViewedQuotesScreenProps) {
    const [viewedQuotes, setViewedQuotes] = useState<ViewedQuote[]>([]);
    const [loading, setLoading] = useState<boolean>(true);
    const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);
    const [showDeleteWarning, setShowDeleteWarning] = useState<boolean>(false);
    const [deleting, setDeleting] = useState<boolean>(false);
    const [searchTerm, setSearchTerm] = useState<string>('');
    const [sortField, setSortField] = useState<'id' | 'quote' | 'author' | 'favourite'>('id');
    const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('desc');
    const { isOpen, retryCount } = useBackendRestartNotification();

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

    const handleDeleteAll = async () => {
        try {
            setDeleting(true);
            await quoteApi.deleteAllViewedQuotes();
            setViewedQuotes([]);
            showToast('All viewed quotes and liked quotes have been deleted. Next quote will start from the beginning.', 'success');
            setShowDeleteWarning(false);
            
            // Call the callback to reset state in App.tsx
            if (onDeleteAll) {
                onDeleteAll();
            }
        } catch (error) {
            console.error('Failed to delete all viewed quotes:', error);
            showToast('Failed to delete all viewed quotes', 'error');
        } finally {
            setDeleting(false);
        }
    };

    const handleSort = (field: 'id' | 'quote' | 'author' | 'favourite') => {
        if (sortField === field) {
            setSortDirection(sortDirection === 'asc' ? 'desc' : 'asc');
        } else {
            setSortField(field);
            setSortDirection('asc');
        }
    };

    const filteredAndSortedQuotes = viewedQuotes
        .filter(quote => 
            quote.quoteText.toLowerCase().includes(searchTerm.toLowerCase()) ||
            quote.author.toLowerCase().includes(searchTerm.toLowerCase())
        )
        .sort((a, b) => {
            let comparison = 0;
            
            switch (sortField) {
                case 'id':
                    comparison = a.id - b.id;
                    break;
                case 'quote':
                    comparison = a.quoteText.localeCompare(b.quoteText);
                    if (comparison === 0) comparison = a.id - b.id;
                    break;
                case 'author':
                    comparison = a.author.localeCompare(b.author);
                    if (comparison === 0) comparison = a.id - b.id;
                    break;
                case 'favourite':
                    comparison = (a.isLiked ? 1 : 0) - (b.isLiked ? 1 : 0);
                    if (comparison === 0) comparison = a.id - b.id;
                    break;
            }
            
            return sortDirection === 'asc' ? comparison : -comparison;
        });

    return (
        <div className="viewed-quotes-screen">
            <BackendRestartNotification isOpen={isOpen} retryCount={retryCount} />
            <div className="viewed-quotes-header">
                <button className="back-button" onClick={onBack}>
                    ← Back
                </button>
                <h2>My Viewed Quotes</h2>
                {viewedQuotes.length > 0 && (
                    <button 
                        className="delete-all-button" 
                        onClick={() => setShowDeleteWarning(true)}
                        disabled={deleting}
                    >
                        Delete All
                    </button>
                )}
            </div>

            <div className="search-controls">
                <input
                    type="text"
                    placeholder="Search by quote or author..."
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                    className="search-input"
                />
            </div>

            {loading ? (
                <div className="loading">Loading viewed quotes...</div>
            ) : filteredAndSortedQuotes.length === 0 ? (
                <div className="empty-state">
                    No quotes found matching "{searchTerm}".
                </div>
            ) : (
                <div className="viewed-quotes-table-container">
                    <table className="viewed-quotes-table">
                        <thead>
                            <tr>
                                <th 
                                    className="sortable-header"
                                    onClick={() => handleSort('id')}
                                >
                                    ID
                                    {sortField === 'id' && (
                                        <span className="sort-indicator">
                                            {sortDirection === 'asc' ? ' ↑' : ' ↓'}
                                        </span>
                                    )}
                                </th>
                                <th 
                                    className="sortable-header"
                                    onClick={() => handleSort('quote')}
                                >
                                    Quote
                                    {sortField === 'quote' && (
                                        <span className="sort-indicator">
                                            {sortDirection === 'asc' ? ' ↑' : ' ↓'}
                                        </span>
                                    )}
                                </th>
                                <th 
                                    className="sortable-header"
                                    onClick={() => handleSort('author')}
                                >
                                    Author
                                    {sortField === 'author' && (
                                        <span className="sort-indicator">
                                            {sortDirection === 'asc' ? ' ↑' : ' ↓'}
                                        </span>
                                    )}
                                </th>
                                <th 
                                    className="sortable-header"
                                    onClick={() => handleSort('favourite')}
                                >
                                    Favourite
                                    {sortField === 'favourite' && (
                                        <span className="sort-indicator">
                                            {sortDirection === 'asc' ? ' ↑' : ' ↓'}
                                        </span>
                                    )}
                                </th>
                            </tr>
                        </thead>
                        <tbody>
                            {filteredAndSortedQuotes.map((quote) => (
                                <tr key={quote.id}>
                                    <td className="id-cell">{quote.id}</td>
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

            {showDeleteWarning && (
                <div className="warning-overlay">
                    <div className="warning-dialog">
                        <h3>⚠️ Warning</h3>
                        <p>This will permanently delete:</p>
                        <ul>
                            <li>All your viewed quotes</li>
                            <li>All your liked quotes</li>
                        </ul>
                        <p>The next time you request a new quote, you will start from the beginning (Quote #1).</p>
                        <div className="warning-actions">
                            <button 
                                className="cancel-button" 
                                onClick={() => setShowDeleteWarning(false)}
                                disabled={deleting}
                            >
                                Cancel
                            </button>
                            <button 
                                className="confirm-delete-button" 
                                onClick={handleDeleteAll}
                                disabled={deleting}
                            >
                                {deleting ? 'Deleting...' : 'Delete All'}
                            </button>
                        </div>
                    </div>
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
