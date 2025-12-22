import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import App from './App';
import { Quote } from './types/Quote';

// Mock the quoteApi module
vi.mock('./api/quoteApi', () => ({
    __esModule: true,
    default: {
        getQuote: vi.fn(),
        getUniqueQuote: vi.fn(),
        likeQuote: vi.fn(),
        getLikedQuotes: vi.fn(),
        unlikeQuote: vi.fn(),
        reorderLikedQuote: vi.fn(),
        getQuoteById: vi.fn(),
        getPreviousQuote: vi.fn(),
        getNextQuote: vi.fn(),
        getUserProgress: vi.fn(),
        getViewedQuotes: vi.fn(),
    },
}));

// Mock the AuthContext
vi.mock('./contexts/AuthContext', () => ({
    useAuth: vi.fn(),
}));

// Mock the FavouritesComponent
vi.mock('./components/FavouritesComponent.tsx', () => ({
    __esModule: true,
    default: vi.fn(() => <div data-testid="favourites-component">Favourites</div>),
    FavouritesComponentHandle: vi.fn(),
}));

// Mock the management screen components
vi.mock('./components/ManagementScreen.tsx', () => ({
    __esModule: true,
    default: vi.fn(() => <div data-testid="management-screen">Management</div>),
}));

vi.mock('./components/ManageFavouritesScreen.tsx', () => ({
    __esModule: true,
    default: vi.fn(() => <div data-testid="manage-favourites-screen">Manage Favourites</div>),
}));

vi.mock('./components/ViewedQuotesScreen.tsx', () => ({
    __esModule: true,
    default: vi.fn(() => <div data-testid="viewed-quotes-screen">Viewed Quotes</div>),
}));

// Mock the Login component
vi.mock('./components/Login.tsx', () => ({
    __esModule: true,
    default: vi.fn(() => <div data-testid="login-component">Login</div>),
}));

// Mock the BackendRestartNotification component
vi.mock('./components/BackendRestartNotification', () => ({
    __esModule: true,
    BackendRestartNotification: vi.fn(() => null),
    useBackendRestartNotification: () => ({
        isOpen: false,
        retryCount: 0,
    }),
}));

// Import the mocked modules after all mocks are defined
import quoteApi from './api/quoteApi';
import { useAuth } from './contexts/AuthContext';

const mockQuoteApi = vi.mocked(quoteApi);
const mockUseAuth = vi.mocked(useAuth);



// Default mock auth context value (unauthenticated)
const mockAuthContextValue = {
    isAuthenticated: false,
    isLoading: false,
    user: null as any,
    signIn: vi.fn(),
    signOut: vi.fn(),
    hasRole: vi.fn((_role: string) => false),
    userGroups: [] as string[],
    needsUsernameSetup: false,
    setNeedsUsernameSetup: vi.fn(),
    signUp: vi.fn(),
    confirmSignUp: vi.fn(),
    refreshUserAttributes: vi.fn(),
};

// Helper function to render the App with test providers
const renderApp = (authContextValue = mockAuthContextValue) => {
    mockUseAuth.mockReturnValue(authContextValue);
    const queryClient = new QueryClient({
        defaultOptions: {
            queries: {
                retry: false,
                gcTime: 0,
            },
        },
    });

    return render(
        <QueryClientProvider client={queryClient}>
            <App />
        </QueryClientProvider>
    );
};


describe('App Component', () => {
    const mockQuote1: Quote = {
        id: 1,
        quoteText: 'The only way to do great work is to love what you do.',
        author: 'Steve Jobs',
        liked: false,
    };

    const mockQuote2: Quote = {
        id: 2,
        quoteText: 'Innovation distinguishes between a leader and a follower.',
        author: 'Steve Jobs',
        liked: false,
    };

    const mockQuote3: Quote = {
        id: 3,
        quoteText: 'Stay hungry, stay foolish.',
        author: 'Steve Jobs',
        liked: false,
    };

    beforeEach(() => {
        vi.clearAllMocks();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('Initial Load', () => {
        it('should display loading state initially', () => {
            mockQuoteApi.getQuote.mockImplementation(
                () => new Promise(() => {}) // Never resolves
            );

            renderApp();

            expect(screen.getByText('"Loading..."')).toBeInTheDocument();
        });

        it('should fetch and display the first quote on mount', async () => {
            mockQuoteApi.getQuote.mockResolvedValue(mockQuote1);

            renderApp();

            await waitFor(() => {
                expect(screen.getByText(`"${mockQuote1.quoteText}"`)).toBeInTheDocument();
                expect(screen.getByText(mockQuote1.author)).toBeInTheDocument();
            });

            expect(mockQuoteApi.getQuote).toHaveBeenCalledTimes(1);
        });

        it('should handle API errors gracefully', async () => {
            const errorMessage = 'Failed to fetch quote';
            mockQuoteApi.getQuote.mockRejectedValue(new Error(errorMessage));

            renderApp();

            // Wait for loading to complete
            await waitFor(() => {
                expect(screen.queryByText('"Loading..."')).not.toBeInTheDocument();
            });

            // Check for error message or retry button
            const errorElement = await screen.findByText(/error|failed|try again/i);
            expect(errorElement).toBeInTheDocument();
        });
    });

    describe('New Quote Button', () => {
        it('should fetch and display a new quote when clicked', async () => {
            mockQuoteApi.getQuote.mockResolvedValue(mockQuote1);
            mockQuoteApi.getUniqueQuote.mockResolvedValue(mockQuote2);

            renderApp();

            // Wait for initial quote to load
            await waitFor(() => {
                expect(screen.getByText(`"${mockQuote1.quoteText}"`)).toBeInTheDocument();
            });

            // Click new quote button
            const newQuoteButton = screen.getByRole('button', { name: /new quote/i });
            fireEvent.click(newQuoteButton);

            await waitFor(() => {
                expect(screen.getByText(`"${mockQuote2.quoteText}"`)).toBeInTheDocument();
                expect(screen.getByText(mockQuote2.author)).toBeInTheDocument();
            });

            expect(mockQuoteApi.getUniqueQuote).toHaveBeenCalledWith([mockQuote1]);
        });

        it('should disable the button while loading', async () => {
            mockQuoteApi.getQuote.mockResolvedValue(mockQuote1);
            mockQuoteApi.getUniqueQuote.mockImplementation(
                () => new Promise(() => {}) // Never resolves
            );

            renderApp();

            await waitFor(() => {
                expect(screen.getByText(`"${mockQuote1.quoteText}"`)).toBeInTheDocument();
            });

            const newQuoteButton = screen.getByRole('button', { name: /new quote/i });
            fireEvent.click(newQuoteButton);

            await waitFor(() => {
                expect(newQuoteButton).toBeDisabled();
                expect(screen.getByText('Loading...')).toBeInTheDocument();
            });
        });
    });

    describe('Like Button', () => {
        const authenticatedUserWithRole = {
            isAuthenticated: true,
            isLoading: false,
            user: { username: 'testuser' },
            signIn: vi.fn(),
            signOut: vi.fn(),
            hasRole: vi.fn((role: string) => role === 'USER'),
            userGroups: ['USER'],
            needsUsernameSetup: false,
            setNeedsUsernameSetup: vi.fn(),
            signUp: vi.fn(),
            confirmSignUp: vi.fn(),
            refreshUserAttributes: vi.fn(),
        };

        it('should like a quote when clicked', async () => {
            const likedQuote = { ...mockQuote1, liked: true };
            mockQuoteApi.getQuote.mockResolvedValue(mockQuote1);
            mockQuoteApi.likeQuote.mockResolvedValue(likedQuote);

            renderApp(authenticatedUserWithRole);

            await waitFor(() => {
                expect(screen.getByText(`"${mockQuote1.quoteText}"`)).toBeInTheDocument();
            });

            const likeButton = screen.getByRole('button', { name: /^like$/i });
            expect(likeButton).not.toBeDisabled();

            fireEvent.click(likeButton);

            await waitFor(() => {
                expect(mockQuoteApi.likeQuote).toHaveBeenCalledWith(mockQuote1);
                expect(likeButton).toBeDisabled();
            });
        });

        it('should disable the button while liking', async () => {
            mockQuoteApi.getQuote.mockResolvedValue(mockQuote1);
            mockQuoteApi.likeQuote.mockImplementation(
                () => new Promise(() => {}) // Never resolves
            );

            renderApp(authenticatedUserWithRole);

            await waitFor(() => {
                expect(screen.getByText(`"${mockQuote1.quoteText}"`)).toBeInTheDocument();
            });

            const likeButton = screen.getByRole('button', { name: /^like$/i });
            fireEvent.click(likeButton);

            await waitFor(() => {
                expect(likeButton).toBeDisabled();
            });
        });

        it('should disable the button if quote is already liked', async () => {
            const likedQuote = { ...mockQuote1, liked: true };
            mockQuoteApi.getQuote.mockResolvedValue(likedQuote);
            mockQuoteApi.getLikedQuotes.mockResolvedValue([likedQuote]);

            renderApp(authenticatedUserWithRole);

            await waitFor(() => {
                expect(screen.getByText(`"${likedQuote.quoteText}"`)).toBeInTheDocument();
            });

            const likeButton = screen.getByRole('button', { name: /^like$/i });
            expect(likeButton).toBeDisabled();
        });
    });

    describe('Navigation Buttons', () => {
        beforeEach(() => {
            mockQuoteApi.getQuoteById.mockImplementation((id) => {
                const quotes = [mockQuote1, mockQuote2, mockQuote3];
                return Promise.resolve(quotes[id - 1]);
            });

            mockQuoteApi.getPreviousQuote.mockImplementation((currentId) => {
                if (currentId === 1) return Promise.resolve(mockQuote1);
                return Promise.resolve({
                    id: currentId - 1,
                    quoteText: `Quote ${currentId - 1}`,
                    author: `Author ${currentId - 1}`,
                    liked: false
                });
            });

            mockQuoteApi.getNextQuote.mockImplementation((currentId) => {
                if (currentId === 3) return Promise.resolve(mockQuote3);
                return Promise.resolve({
                    id: currentId + 1,
                    quoteText: `Quote ${currentId + 1}`,
                    author: `Author ${currentId + 1}`,
                    liked: false
                });
            });
        });

        it('should navigate to previous quote', async () => {
            renderApp();

            // Wait for first quote
            await waitFor(() => {
                expect(screen.getByText(`"${mockQuote1.quoteText}"`)).toBeInTheDocument();
            });

            // Get second quote
            const newQuoteButton = screen.getByRole('button', { name: /new quote/i });
            fireEvent.click(newQuoteButton);

            await waitFor(() => {
                expect(screen.getByText(`"${mockQuote2.quoteText}"`)).toBeInTheDocument();
            });

            // Navigate back to first quote
            const previousButton = screen.getByRole('button', { name: /previous/i });
            fireEvent.click(previousButton);

            expect(screen.getByText(`"${mockQuote1.quoteText}"`)).toBeInTheDocument();
        });

        it('should navigate to next quote', async () => {
            renderApp();

            await waitFor(() => {
                expect(screen.getByText(`"${mockQuote1.quoteText}"`)).toBeInTheDocument();
            });

            // Get second quote
            const newQuoteButton = screen.getByRole('button', { name: /new quote/i });
            fireEvent.click(newQuoteButton);

            await waitFor(() => {
                expect(screen.getByText(`"${mockQuote2.quoteText}"`)).toBeInTheDocument();
            });

            // Navigate back
            const previousButton = screen.getByRole('button', { name: /previous/i });
            fireEvent.click(previousButton);

            expect(screen.getByText(`"${mockQuote1.quoteText}"`)).toBeInTheDocument();

            // Navigate forward
            const nextButton = screen.getByRole('button', { name: /next/i });
            fireEvent.click(nextButton);

            expect(screen.getByText(`"${mockQuote2.quoteText}"`)).toBeInTheDocument();
        });

        it('should jump to first quote', async () => {
            renderApp();

            await waitFor(() => {
                expect(screen.getByText(`"${mockQuote1.quoteText}"`)).toBeInTheDocument();
            });

            // Get second and third quotes
            const newQuoteButton = screen.getByRole('button', { name: /new quote/i });
            fireEvent.click(newQuoteButton);

            await waitFor(() => {
                expect(screen.getByText(`"${mockQuote2.quoteText}"`)).toBeInTheDocument();
            });

            fireEvent.click(newQuoteButton);

            await waitFor(() => {
                expect(screen.getByText(`"${mockQuote3.quoteText}"`)).toBeInTheDocument();
            });

            // Jump to first
            const firstButton = screen.getByRole('button', { name: /first/i });
            fireEvent.click(firstButton);

            expect(screen.getByText(`"${mockQuote1.quoteText}"`)).toBeInTheDocument();
        });

        it('should jump to last quote', async () => {
            renderApp();

            await waitFor(() => {
                expect(screen.getByText(`"${mockQuote1.quoteText}"`)).toBeInTheDocument();
            });

            // Get second and third quotes
            const newQuoteButton = screen.getByRole('button', { name: /new quote/i });
            fireEvent.click(newQuoteButton);

            await waitFor(() => {
                expect(screen.getByText(`"${mockQuote2.quoteText}"`)).toBeInTheDocument();
            });

            fireEvent.click(newQuoteButton);

            await waitFor(() => {
                expect(screen.getByText(`"${mockQuote3.quoteText}"`)).toBeInTheDocument();
            });

            // Navigate to first
            const firstButton = screen.getByRole('button', { name: /first/i });
            fireEvent.click(firstButton);

            expect(screen.getByText(`"${mockQuote1.quoteText}"`)).toBeInTheDocument();

            // Jump to last
            const lastButton = screen.getByRole('button', { name: /last/i });
            fireEvent.click(lastButton);

            expect(screen.getByText(`"${mockQuote3.quoteText}"`)).toBeInTheDocument();
        });

        it('should disable previous button when at first quote', async () => {
            renderApp();

            await waitFor(() => {
                expect(screen.getByText(`"${mockQuote1.quoteText}"`)).toBeInTheDocument();
            });

            const previousButton = screen.getByRole('button', { name: /previous/i });
            expect(previousButton).toBeDisabled();
        });

        it('should disable next button when at last quote', async () => {
            renderApp();

            await waitFor(() => {
                expect(screen.getByText(`"${mockQuote1.quoteText}"`)).toBeInTheDocument();
            });

            const nextButton = screen.getByRole('button', { name: /next/i });
            expect(nextButton).toBeDisabled();
        });
    });

    describe('UI Elements', () => {
        it('should display the logo', async () => {
            mockQuoteApi.getQuote.mockResolvedValue(mockQuote1);

            renderApp();

            await waitFor(() => {
                expect(screen.getByText('CODE-BULTER')).toBeInTheDocument();
                expect(screen.getByText('Quote')).toBeInTheDocument();
            });
        });

        it('should render the FavouritesComponent', async () => {
            mockQuoteApi.getQuote.mockResolvedValue(mockQuote1);

            renderApp();

            await waitFor(() => {
                expect(screen.getByTestId('favourites-component')).toBeInTheDocument();
            });
        });
    });
});
