import { useEffect, useState } from 'react';
import './QuoteManagementScreen.css';
import adminApi, { QuoteWithLikeCount } from '../api/adminApi';
import { Toast } from './Toast';

interface QuoteManagementScreenProps {
    onBack: () => void;
}

export function QuoteManagementScreen({ onBack }: QuoteManagementScreenProps) {
    const [quotes, setQuotes] = useState<QuoteWithLikeCount[]>([]);
    const [totalCount, setTotalCount] = useState<number>(0);
    const [totalLikes, setTotalLikes] = useState<number>(0);
    const [page, setPage] = useState<number>(1);
    const [pageSize, setPageSize] = useState<number>(50);
    const [totalPages, setTotalPages] = useState<number>(0);
    
    const [quoteTextSearch, setQuoteTextSearch] = useState<string>("");
    const [authorSearch, setAuthorSearch] = useState<string>("");
    const [debouncedQuoteText, setDebouncedQuoteText] = useState<string>("");
    const [debouncedAuthor, setDebouncedAuthor] = useState<string>("");
    const [loading, setLoading] = useState<boolean>(true);
    const [searching, setSearching] = useState<boolean>(false);
    const [addingQuotes, setAddingQuotes] = useState<boolean>(false);
    const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);
    const [sortBy, setSortBy] = useState<'id' | 'quoteText' | 'author' | 'likeCount'>('id');
    const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('asc');

    // Debounce search inputs
    useEffect(() => {
        const timer = setTimeout(() => {
            setDebouncedQuoteText(quoteTextSearch);
        }, 500);
        return () => clearTimeout(timer);
    }, [quoteTextSearch]);

    useEffect(() => {
        const timer = setTimeout(() => {
            setDebouncedAuthor(authorSearch);
        }, 500);
        return () => clearTimeout(timer);
    }, [authorSearch]);

    useEffect(() => {
        loadQuotes();
    }, [page, pageSize, debouncedQuoteText, debouncedAuthor, sortBy, sortOrder]);

    const loadQuotes = async () => {
        try {
            // Only show loading spinner on initial load
            if (quotes.length === 0) {
                setLoading(true);
            } else {
                setSearching(true);
            }
            const response = await adminApi.getQuotes(page, pageSize, debouncedQuoteText, debouncedAuthor, sortBy, sortOrder);
            setQuotes(response.quotes);
            setTotalCount(response.totalCount);
            setTotalPages(response.totalPages);
            
            // Calculate total likes from current page
            if (response.quotes.length > 0) {
                const pageTotal = response.quotes.reduce((sum, quote) => sum + quote.likeCount, 0);
                setTotalLikes(pageTotal);
            } else {
                setTotalLikes(0);
            }
        } catch (error) {
            console.error('Failed to load quotes:', error);
            showToast('Failed to load quotes', 'error');
        } finally {
            setLoading(false);
            setSearching(false);
        }
    };

    const handleAddQuotes = async () => {
        try {
            setAddingQuotes(true);
            const response = await adminApi.fetchAndAddNewQuotes();
            setTotalCount(response.totalQuotes);
            setPage(1); // Reset to first page
            showToast(`Successfully added ${response.quotesAdded} new quotes`, 'success');
            await loadQuotes();
        } catch (error) {
            console.error('Failed to add quotes:', error);
            showToast('Failed to add quotes', 'error');
        } finally {
            setAddingQuotes(false);
        }
    };

    const handlePageSizeChange = (newPageSize: number) => {
        setPageSize(newPageSize);
        setPage(1);
    };

    const handleSort = (column: 'id' | 'quoteText' | 'author' | 'likeCount') => {
        if (sortBy === column) {
            setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc');
        } else {
            setSortBy(column);
            setSortOrder('asc');
        }
    };

    const handleClearSearch = () => {
        setQuoteTextSearch("");
        setAuthorSearch("");
        setDebouncedQuoteText("");
        setDebouncedAuthor("");
        setPage(1);
    };

    const handlePageJump = (newPage: number) => {
        const validPage = Math.max(1, Math.min(totalPages, newPage));
        setPage(validPage);
    };

    const showToast = (message: string, type: 'success' | 'error') => {
        setToast({ message, type });
    };

    return (
        <div className="quote-management-screen">
            <div className="quote-management-header">
                <button className="back-button" onClick={onBack}>
                    ← Back
                </button>
                <h2>Manage Quotes</h2>
            </div>

            <div className="quote-count-section">
                <div className="quote-stats">
                    <span className="quote-count">Total Quotes: {totalCount.toLocaleString()}</span>
                    <span className="quote-count">Likes on Page: {totalLikes.toLocaleString()}</span>
                </div>
                <button 
                    className="add-quotes-button" 
                    onClick={handleAddQuotes}
                    disabled={addingQuotes}
                >
                    {addingQuotes ? 'Adding Quotes...' : 'Add Quotes from ZEN'}
                </button>
            </div>

            <div className="search-section">
                <input
                    type="text"
                    placeholder="Search by quote text..."
                    value={quoteTextSearch}
                    onChange={(e) => {
                        setQuoteTextSearch(e.target.value);
                        setPage(1);
                    }}
                    className="search-input"
                />
                <input
                    type="text"
                    placeholder="Search by author..."
                    value={authorSearch}
                    onChange={(e) => {
                        setAuthorSearch(e.target.value);
                        setPage(1);
                    }}
                    className="search-input"
                />
                <button 
                    className="clear-search-button"
                    onClick={handleClearSearch}
                >
                    Clear
                </button>
            </div>

            {loading ? (
                <div className="loading">Loading quotes...</div>
            ) : (
                <>
                    <div className={`quotes-table-container ${searching ? 'searching' : ''}`}>
                        {quotes.length === 0 ? (
                            <div className="empty-state-inline">No quotes found.</div>
                        ) : (
                            <table className="quotes-table">
                                <thead>
                                    <tr>
                                        <th onClick={() => handleSort('id')} className="sortable">
                                            ID {sortBy === 'id' && (sortOrder === 'asc' ? '↑' : '↓')}
                                        </th>
                                        <th onClick={() => handleSort('quoteText')} className="sortable">
                                            Quote Text {sortBy === 'quoteText' && (sortOrder === 'asc' ? '↑' : '↓')}
                                        </th>
                                        <th onClick={() => handleSort('author')} className="sortable">
                                            Author {sortBy === 'author' && (sortOrder === 'asc' ? '↑' : '↓')}
                                        </th>
                                        <th onClick={() => handleSort('likeCount')} className="sortable">
                                            Likes {sortBy === 'likeCount' && (sortOrder === 'asc' ? '↑' : '↓')}
                                        </th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {quotes.map((quote) => (
                                        <tr key={quote.id}>
                                            <td className="id-cell">{quote.id}</td>
                                            <td className="quote-text-cell">{quote.quoteText}</td>
                                            <td className="author-cell">{quote.author}</td>
                                            <td className="like-count-cell">{quote.likeCount}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        )}
                    </div>

                    <div className="pagination-section">
                        <div className="page-size-selector">
                            <label>Quotes per page:</label>
                            <select value={pageSize} onChange={(e) => handlePageSizeChange(Number(e.target.value))}>
                                <option value={10}>10</option>
                                <option value={25}>25</option>
                                <option value={50}>50</option>
                                <option value={100}>100</option>
                                <option value={250}>250</option>
                            </select>
                        </div>

                        <div className="pagination-controls">
                            <button 
                                onClick={() => setPage(Math.max(1, page - 1))}
                                disabled={page === 1}
                            >
                                Previous
                            </button>
                            
                            <span className="page-info">
                                Page {page} of {totalPages}
                            </span>
                            
                            <input
                                type="number"
                                min="1"
                                max={totalPages}
                                value={page}
                                onChange={(e) => handlePageJump(Number(e.target.value))}
                                className="page-input"
                            />
                            
                            <button 
                                onClick={() => setPage(Math.min(totalPages, page + 1))}
                                disabled={page === totalPages}
                            >
                                Next
                            </button>
                        </div>
                    </div>
                </>
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
