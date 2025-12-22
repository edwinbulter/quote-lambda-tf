import { BASE_URL } from "../constants/constants";
import {Quote} from "../types/Quote.ts";
import { fetchAuthSession } from 'aws-amplify/auth';
import { withRetry } from '../utils/apiRetry';
import { notifyBackendRestart } from '../components/BackendRestartNotification';

// Helper function to get auth headers
async function getAuthHeaders(): Promise<HeadersInit> {
    try {
        const session = await fetchAuthSession();
        // Use access token which contains "username" claim
        const token = session.tokens?.accessToken?.toString();
        if (token) {
            return {
                'Authorization': token,
            };
        }
    } catch (error) {
        console.error('Failed to get auth token:', error);
    }
    return {};
}

// Define the functions with explicit parameter and return types
async function getQuote(): Promise<Quote> {
    const response = await fetch(`${BASE_URL}/quote`, {
        method: "GET",
    });
    return await response.json();
}

async function getUniqueQuote(receivedQuotes: Quote[]): Promise<Quote> {
    const quoteIds = receivedQuotes.map(quote => quote.id);
    
    return withRetry(
        async () => {
            const response = await fetch(`${BASE_URL}/quote`, {
                method: "POST",
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(quoteIds),
            });
            
            if (!response.ok) {
                throw new Error(`Failed to fetch quote: ${response.status} ${response.statusText}`);
            }
            
            return await response.json();
        },
        {
            onRetry: (attempt, error) => {
                console.log(`Retrying getUniqueQuote (attempt ${attempt})...`, error);
                notifyBackendRestart(true, attempt);
            }
        }
    ).finally(() => {
        notifyBackendRestart(false);
    });
}

async function likeQuote(quote: Quote): Promise<Quote> {
    const authHeaders = await getAuthHeaders();
    
    return withRetry(
        async () => {
            const response = await fetch(`${BASE_URL}/quote/${quote.id}/like`, {
                method: "POST",
                headers: {
                    'Content-Type': 'application/json',
                    ...authHeaders,
                },
            });
            
            if (!response.ok) {
                throw new Error(`Failed to like quote: ${response.status} ${response.statusText}`);
            }
            
            return await response.json();
        },
        {
            onRetry: (attempt, error) => {
                console.log(`Retrying likeQuote (attempt ${attempt})...`, error);
                notifyBackendRestart(true, attempt);
            }
        }
    ).finally(() => {
        notifyBackendRestart(false);
    });
}

async function getLikedQuotes(): Promise<Quote[]> {
    const authHeaders = await getAuthHeaders();
    
    return withRetry(
        async () => {
            const response = await fetch(`${BASE_URL}/quote/liked`, {
                method: "GET",
                headers: {
                    ...authHeaders,
                },
            });
            
            if (!response.ok) {
                throw new Error(`Failed to fetch liked quotes: ${response.status} ${response.statusText}`);
            }
            
            return await response.json();
        },
        {
            onRetry: (attempt, error) => {
                console.log(`Retrying getLikedQuotes (attempt ${attempt})...`, error);
                notifyBackendRestart(true, attempt);
            }
        }
    ).finally(() => {
        notifyBackendRestart(false);
    });
}

/**
 * Get a quote for authenticated users
 * Backend automatically records view and excludes already viewed quotes
 */
async function getAuthenticatedQuote(): Promise<Quote> {
    const authHeaders = await getAuthHeaders();
    
    return withRetry(
        async () => {
            const response = await fetch(`${BASE_URL}/quote`, {
                method: "GET",
                headers: {
                    ...authHeaders,
                },
            });
            
            if (!response.ok) {
                throw new Error(`Failed to fetch quote: ${response.status} ${response.statusText}`);
            }
            
            return await response.json();
        },
        {
            onRetry: (attempt, error) => {
                console.log(`Retrying getAuthenticatedQuote (attempt ${attempt})...`, error);
                notifyBackendRestart(true, attempt);
            }
        }
    ).finally(() => {
        notifyBackendRestart(false);
    });
}

async function unlikeQuote(quoteId: number): Promise<void> {
    const authHeaders = await getAuthHeaders();
    
    return withRetry(
        async () => {
            const response = await fetch(`${BASE_URL}/quote/${quoteId}/unlike`, {
                method: "DELETE",
                headers: {
                    ...authHeaders,
                },
            });
            
            if (!response.ok) {
                throw new Error(`Failed to unlike quote: ${response.status} ${response.statusText}`);
            }
        },
        {
            onRetry: (attempt, error) => {
                console.log(`Retrying unlikeQuote (attempt ${attempt})...`, error);
                notifyBackendRestart(true, attempt);
            }
        }
    ).finally(() => {
        notifyBackendRestart(false);
    });
}

async function reorderLikedQuote(quoteId: number, order: number): Promise<void> {
    const authHeaders = await getAuthHeaders();
    
    return withRetry(
        async () => {
            const response = await fetch(`${BASE_URL}/quote/${quoteId}/reorder`, {
                method: "PUT",
                headers: {
                    'Content-Type': 'application/json',
                    ...authHeaders,
                },
                body: JSON.stringify({ order }),
            });
            
            if (!response.ok) {
                throw new Error(`Failed to reorder quote: ${response.status} ${response.statusText}`);
            }
        },
        {
            onRetry: (attempt, error) => {
                console.log(`Retrying reorderLikedQuote (attempt ${attempt})...`, error);
                notifyBackendRestart(true, attempt);
            }
        }
    ).finally(() => {
        notifyBackendRestart(false);
    });
}

// New sequential navigation API functions

/**
 * Get a specific quote by ID
 */
async function getQuoteById(quoteId: number): Promise<Quote> {
    const authHeaders = await getAuthHeaders();
    
    return withRetry(
        async () => {
            const response = await fetch(`${BASE_URL}/quote/${quoteId}`, {
                method: "GET",
                headers: {
                    ...authHeaders,
                },
            });
            
            if (!response.ok) {
                throw new Error(`Failed to fetch quote: ${response.status} ${response.statusText}`);
            }
            
            return await response.json();
        },
        {
            onRetry: (attempt, error) => {
                console.log(`Retrying getQuoteById (attempt ${attempt})...`, error);
                notifyBackendRestart(true, attempt);
            }
        }
    ).finally(() => {
        notifyBackendRestart(false);
    });
}

/**
 * Get previous quote for sequential navigation
 */
async function getPreviousQuote(currentQuoteId: number): Promise<Quote> {
    const authHeaders = await getAuthHeaders();
    
    return withRetry(
        async () => {
            const response = await fetch(`${BASE_URL}/quote/${currentQuoteId}/previous`, {
                method: "GET",
                headers: {
                    ...authHeaders,
                },
            });
            
            if (!response.ok) {
                throw new Error(`Failed to fetch previous quote: ${response.status} ${response.statusText}`);
            }
            
            return await response.json();
        },
        {
            onRetry: (attempt, error) => {
                console.log(`Retrying getPreviousQuote (attempt ${attempt})...`, error);
                notifyBackendRestart(true, attempt);
            }
        }
    ).finally(() => {
        notifyBackendRestart(false);
    });
}

/**
 * Get next quote for sequential navigation
 */
async function getNextQuote(currentQuoteId: number): Promise<Quote> {
    const authHeaders = await getAuthHeaders();
    
    return withRetry(
        async () => {
            const response = await fetch(`${BASE_URL}/quote/${currentQuoteId}/next`, {
                method: "GET",
                headers: {
                    ...authHeaders,
                },
            });
            
            if (!response.ok) {
                throw new Error(`Failed to fetch next quote: ${response.status} ${response.statusText}`);
            }
            
            return await response.json();
        },
        {
            onRetry: (attempt, error) => {
                console.log(`Retrying getNextQuote (attempt ${attempt})...`, error);
                notifyBackendRestart(true, attempt);
            }
        }
    ).finally(() => {
        notifyBackendRestart(false);
    });
}

/**
 * Get user's current progress (lastQuoteId)
 */
async function getUserProgress(): Promise<{ lastQuoteId: number; username: string; updatedAt: number }> {
    const authHeaders = await getAuthHeaders();
    
    return withRetry(
        async () => {
            const response = await fetch(`${BASE_URL}/quote/progress`, {
                method: "GET",
                headers: {
                    ...authHeaders,
                },
            });
            
            if (!response.ok) {
                throw new Error(`Failed to fetch user progress: ${response.status} ${response.statusText}`);
            }
            
            return await response.json();
        },
        {
            onRetry: (attempt, error) => {
                console.log(`Retrying getUserProgress (attempt ${attempt})...`, error);
                notifyBackendRestart(true, attempt);
            }
        }
    ).finally(() => {
        notifyBackendRestart(false);
    });
}

/**
 * Get all viewed quotes (1 to lastQuoteId)
 */
async function getViewedQuotes(): Promise<Quote[]> {
    const authHeaders = await getAuthHeaders();
    
    return withRetry(
        async () => {
            const response = await fetch(`${BASE_URL}/quote/viewed`, {
                method: "GET",
                headers: {
                    ...authHeaders,
                },
            });
            
            if (!response.ok) {
                throw new Error(`Failed to fetch viewed quotes: ${response.status} ${response.statusText}`);
            }
            
            return await response.json();
        },
        {
            onRetry: (attempt, error) => {
                console.log(`Retrying getViewedQuotes (attempt ${attempt})...`, error);
                notifyBackendRestart(true, attempt);
            }
        }
    ).finally(() => {
        notifyBackendRestart(false);
    });
}

export default {
    getQuote,
    getUniqueQuote,
    getAuthenticatedQuote,
    likeQuote,
    unlikeQuote,
    getLikedQuotes,
    reorderLikedQuote,
    // Sequential navigation functions
    getQuoteById,
    getPreviousQuote,
    getNextQuote,
    getUserProgress,
    getViewedQuotes,
};