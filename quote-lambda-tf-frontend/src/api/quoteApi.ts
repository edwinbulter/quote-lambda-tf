import { BASE_URL } from "../constants/constants";
import {Quote} from "../types/Quote.ts";
import { fetchAuthSession } from 'aws-amplify/auth';

// Helper function to get auth headers
async function getAuthHeaders(): Promise<HeadersInit> {
    try {
        const session = await fetchAuthSession();
        const token = session.tokens?.idToken?.toString();
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
    const response = await fetch(`${BASE_URL}/quote`, {
        method: "POST",
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(quoteIds),
    });
    return await response.json();
}

async function likeQuote(quote: Quote): Promise<Quote> {
    const authHeaders = await getAuthHeaders();
    const response = await fetch(`${BASE_URL}/quote/${quote.id}/like`, {
        method: "PATCH",
        headers: {
            ...authHeaders,
        },
    });
    
    if (!response.ok) {
        throw new Error(`Failed to like quote: ${response.status} ${response.statusText}`);
    }
    
    return await response.json();
}

async function getLikedQuotes(): Promise<Quote[]> {
    const response = await fetch(`${BASE_URL}/quote/liked`, {
        method: "GET",
    });
    return await response.json();
}

export default {
    getQuote,
    getUniqueQuote,
    likeQuote,
    getLikedQuotes,
};