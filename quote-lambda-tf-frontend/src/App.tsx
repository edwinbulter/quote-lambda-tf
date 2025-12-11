import './App.scss';
import React, { useEffect, useRef, useState } from "react";
import quoteApi from "./api/quoteApi";
import FavouritesComponent, { FavouritesComponentHandle } from "./components/FavouritesComponent.tsx";

// Import `Quote` interface from the appropriate file
import { Quote } from "./types/Quote"; // Adjust the path based on your project

const App: React.FC = () => {
    const [quote, setQuote] = useState<Quote | null>(null); // Allow `null` for initial state
    const [receivedQuotes, setReceivedQuotes] = useState<Quote[]>([]); // Array of `Quote` objects
    const [loading, setLoading] = useState<boolean>(true); // Loading state
    const [liking, setLiking] = useState<boolean>(false); // Liking state
    const indexRef = useRef<number>(0); // Reference to the current quote index
    const favouritesRef = useRef<FavouritesComponentHandle>(null);

    useEffect(() => {
        fetchFirstQuote(); // Called twice in StrictMode (only in development)
    }, []);

    const fetchFirstQuote = async (): Promise<void> => {
        try {
            setLoading(true);
            const firstQuote = await quoteApi.getQuote(); // Fetch initial quote
            setQuote(firstQuote);
            setReceivedQuotes([firstQuote]); // Store the first quote in the received quotes array
        } catch (error) {
            console.error('Failed to fetch first quote:', error);
        } finally {
            setLoading(false);
        }
    };

    const newQuote = async (): Promise<void> => {
        try {
            setLoading(true);
            const uniqueQuote = await quoteApi.getUniqueQuote(receivedQuotes); // Fetch a unique quote
            setQuote(uniqueQuote);
            indexRef.current = receivedQuotes.length; // Update the reference to the index
            setReceivedQuotes((prevQuotes) => [...prevQuotes, uniqueQuote]); // Add the new quote
        } catch (error) {
            console.error('Failed to fetch new quote:', error);
        } finally {
            setLoading(false);
        }
    };

    const like = async (): Promise<void> => {
        try {
            if (quote && !quote.liked) {
                setLiking(true);
                const updatedQuote = await quoteApi.likeQuote(quote);
                if (updatedQuote && updatedQuote.id === quote.id) {
                    // Update the `liked` property of the current quote in `receivedQuotes` array
                    setReceivedQuotes((prevQuotes) =>
                        prevQuotes.map((item) =>
                            item.id === quote.id ? { ...item, liked: true } : item
                        )
                    );
                    setQuote({ ...quote, liked: true }); // Update the `quote` state
                    if (favouritesRef.current) {
                        favouritesRef.current.reloadFavouriteQuotes();
                    }
                } else {
                    console.log("Failed to like quote for some reason, id=" + quote.id);
                }
            }
        } catch (error) {
            console.error('Failed to like quote:', error);
        } finally {
            setLiking(false);
        }
    };

    const previous = (): void => {
        if (indexRef.current > 0) {
            indexRef.current = indexRef.current - 1;
            setQuote(receivedQuotes[indexRef.current]); // Set the previous quote
        }
    };

    const next = (): void => {
        if (indexRef.current < receivedQuotes.length - 1) {
            indexRef.current = indexRef.current + 1;
            setQuote(receivedQuotes[indexRef.current]); // Set the next quote
        }
    };

    const jumpToFirst = (): void => {
        indexRef.current = 0;
        setQuote(receivedQuotes[indexRef.current]); // Jump to the first quote
    };

    const jumpToLast = (): void => {
        indexRef.current = receivedQuotes.length - 1;
        setQuote(receivedQuotes[indexRef.current]); // Jump to the last quote
    };

    return (
        <div className="app">
            <div className="quoteView">
                <p>
                    "{loading ? "Loading..." : quote?.quoteText || ""}"
                </p>
                <p className="author">
                    {loading ? "" : quote?.author || ""}
                </p>
            </div>
            <div className="buttonBar">
                <div className="logo">
                    <div className="logo-header">CODE-BUTLER</div>
                    <div className="logo-main">Quote</div>
                </div>
                <button className="newQuoteButton" disabled={loading} onClick={newQuote}>
                    {loading ? "Loading..." : "New Quote"}
                </button>
                <button className="likeButton" disabled={liking || !!quote?.liked} onClick={like}>
                    {liking ? "Liking..." : "Like"}
                </button>
                <button className="previousButton" disabled={indexRef.current === 0} onClick={previous}>
                    Previous
                </button>
                <button
                    className="nextButton"
                    disabled={indexRef.current >= receivedQuotes.length - 1}
                    onClick={next}
                >
                    Next
                </button>
                <button className="firstButton" onClick={jumpToFirst}>
                    First
                </button>
                <button className="lastButton" onClick={jumpToLast}>
                    Last
                </button>
            </div>
            <FavouritesComponent ref={favouritesRef}/>
        </div>
    );
};

export default App;