import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import App from './App';
import quoteApi from './api/quoteApi';
import { Quote } from './types/Quote';
import * as AuthContext from './contexts/AuthContext';

// Mock the quoteApi module
vi.mock('./api/quoteApi');

// Mock the FavouritesComponent
vi.mock('./components/FavouritesComponent.tsx', () => ({
    default: vi.fn(() => <div data-testid="favourites-component">Favourites</div>),
    FavouritesComponentHandle: vi.fn(),
}));

// Mock the management screen components
vi.mock('./components/ManagementScreen.tsx', () => ({
    ManagementScreen: vi.fn(() => <div data-testid="management-screen">Management</div>),
}));

vi.mock('./components/ManageFavouritesScreen.tsx', () => ({
    ManageFavouritesScreen: vi.fn(() => <div data-testid="manage-favourites-screen">Manage Favourites</div>),
}));

vi.mock('./components/ViewedQuotesScreen.tsx', () => ({
    ViewedQuotesScreen: vi.fn(() => <div data-testid="viewed-quotes-screen">Viewed Quotes</div>),
}));

// Mock the Login component
vi.mock('./components/Login.tsx', () => ({
    Login: vi.fn(() => <div data-testid="login-component">Login</div>),
}));

// Default mock auth context value (unauthenticated)
const mockAuthContextValue = {
    isAuthenticated: false,
    isLoading: false,
    user: null as any,
    signIn: vi.fn(),
    signOut: vi.fn(),
    hasRole: vi.fn(() => false),
    userGroups: [] as string[],
    needsUsernameSetup: false,
};

// Helper function to render App with mocked AuthContext
const renderApp = (authValue: any = mockAuthContextValue) => {
    vi.spyOn(AuthContext, 'useAuth').mockReturnValue(authValue);
    return render(<App />);
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
            vi.mocked(quoteApi.getQuote).mockImplementation(
                () => new Promise(() => {}) // Never resolves
            );

            renderApp();

            expect(screen.getByText('"Loading..."')).toBeInTheDocument();
        });

        it('should fetch and display the first quote on mount', async () => {
            vi.mocked(quoteApi.getQuote).mockResolvedValue(mockQuote1);

            renderApp();

            await waitFor(() => {
                expect(screen.getByText(`"${mockQuote1.quoteText}"`)).toBeInTheDocument();
                expect(screen.getByText(mockQuote1.author)).toBeInTheDocument();
            });

            expect(quoteApi.getQuote).toHaveBeenCalledTimes(1);
        });

        it('should handle API errors gracefully', async () => {
            vi.mocked(quoteApi.getQuote).mockRejectedValue(new Error('API Error'));

            renderApp();

            await waitFor(() => {
                expect(screen.queryByText('"Loading..."')).not.toBeInTheDocument();
            });
        });
    });

    describe('New Quote Button', () => {
        it('should fetch and display a new quote when clicked', async () => {
            vi.mocked(quoteApi.getQuote).mockResolvedValue(mockQuote1);
            vi.mocked(quoteApi.getUniqueQuote).mockResolvedValue(mockQuote2);

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

            expect(quoteApi.getUniqueQuote).toHaveBeenCalledWith([mockQuote1]);
        });

        it('should disable the button while loading', async () => {
            vi.mocked(quoteApi.getQuote).mockResolvedValue(mockQuote1);
            vi.mocked(quoteApi.getUniqueQuote).mockImplementation(
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
        };

        it('should like a quote when clicked', async () => {
            const likedQuote = { ...mockQuote1, liked: true };
            vi.mocked(quoteApi.getQuote).mockResolvedValue(mockQuote1);
            vi.mocked(quoteApi.likeQuote).mockResolvedValue(likedQuote);

            renderApp(authenticatedUserWithRole);

            await waitFor(() => {
                expect(screen.getByText(`"${mockQuote1.quoteText}"`)).toBeInTheDocument();
            });

            const likeButton = screen.getByRole('button', { name: /^like$/i });
            expect(likeButton).not.toBeDisabled();

            fireEvent.click(likeButton);

            await waitFor(() => {
                expect(quoteApi.likeQuote).toHaveBeenCalledWith(mockQuote1);
                expect(likeButton).toBeDisabled();
            });
        });

        it('should disable the button while liking', async () => {
            vi.mocked(quoteApi.getQuote).mockResolvedValue(mockQuote1);
            vi.mocked(quoteApi.likeQuote).mockImplementation(
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
                expect(screen.getByText('Liking...')).toBeInTheDocument();
            });
        });

        it('should disable the button if quote is already liked', async () => {
            const likedQuote = { ...mockQuote1, liked: true };
            vi.mocked(quoteApi.getQuote).mockResolvedValue(likedQuote);

            renderApp(authenticatedUserWithRole);

            await waitFor(() => {
                expect(screen.getByText(`"${mockQuote1.quoteText}"`)).toBeInTheDocument();
            });

            const likeButton = screen.getByRole('button', { name: /^like$/i });
            expect(likeButton).toBeDisabled();
        });
    });

    describe('Navigation Buttons', () => {
        beforeEach(async () => {
            vi.mocked(quoteApi.getQuote).mockResolvedValue(mockQuote1);
            vi.mocked(quoteApi.getUniqueQuote)
                .mockResolvedValueOnce(mockQuote2)
                .mockResolvedValueOnce(mockQuote3);
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
            vi.mocked(quoteApi.getQuote).mockResolvedValue(mockQuote1);

            renderApp();

            await waitFor(() => {
                expect(screen.getByText('CODE-BULTER')).toBeInTheDocument();
                expect(screen.getByText('Quote')).toBeInTheDocument();
            });
        });

        it('should render the FavouritesComponent', async () => {
            vi.mocked(quoteApi.getQuote).mockResolvedValue(mockQuote1);

            renderApp();

            await waitFor(() => {
                expect(screen.getByTestId('favourites-component')).toBeInTheDocument();
            });
        });
    });
});
