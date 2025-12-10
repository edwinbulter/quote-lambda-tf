import './FavouritesComponent.css';
import {forwardRef, useEffect, useImperativeHandle, useState} from "react";
import quoteApi from "../api/quoteApi";
import {Quote} from "../types/Quote.ts";

export type FavouritesComponentHandle = {
    reloadFavouriteQuotes: () => void;
};

const FavouritesComponent = forwardRef<FavouritesComponentHandle>((_, ref) => {

    const [messages, setMessages] = useState<string[]>([]); // List of messages as strings
    const [loading, setLoading] = useState<boolean>(false); // Loading state as a boolean

    useEffect(() => {
        loadFavouriteQuotes();
    }, []);

    useImperativeHandle(ref, () => ({
        reloadFavouriteQuotes() {
            loadFavouriteQuotes();
        }
    }));

    const loadFavouriteQuotes = async (): Promise<void> => {
        try {
            setLoading(true);
            const quotes: Quote[] = await quoteApi.getLikedQuotes(); // Assume this returns an array of `Quote`
            setMessages(quotes.map((quote) => `${quote.quoteText} - ${quote.author}`));
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="eventBox">
            <button
                className="favouritesButton"
                disabled={loading}
                onClick={loadFavouriteQuotes}
            >
                {loading ? "Loading Favourite Quotes..." : "Favourite Quotes (Click to refresh)"}
            </button>
            <div className="messageBox">
                {messages.map((message, index) => (
                    <div
                        className={index % 2 === 0 ? "messageEven" : "messageOdd"}
                        key={index}
                    >
                        {message}
                    </div>
                ))}
            </div>
        </div>
    );
});

export default FavouritesComponent;