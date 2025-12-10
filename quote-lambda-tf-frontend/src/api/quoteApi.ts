import { BASE_URL } from "../constants/constants";
import {Quote} from "../types/Quote.ts";

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
    const response = await fetch(`${BASE_URL}/quote/${quote.id}/like`, {
        method: "PATCH",
    });
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