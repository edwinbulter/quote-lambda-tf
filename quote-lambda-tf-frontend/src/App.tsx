import './App.scss';
import React, { useEffect, useRef, useState } from "react";
import quoteApi from "./api/quoteApi";
import FavouritesComponent, { FavouritesComponentHandle } from "./components/FavouritesComponent.tsx";

// Import `Quote` interface from the appropriate file
import { Quote } from "./types/Quote"; // Adjust the path based on your project
import { useAuth } from './contexts/AuthContext';
import { Login } from './components/Login';

const App: React.FC = () => {
    const { isAuthenticated, isLoading, signOut, user, hasRole, userGroups, needsUsernameSetup } = useAuth();
    const [quote, setQuote] = useState<Quote | null>(null); // Allow `null` for initial state
    const [receivedQuotes, setReceivedQuotes] = useState<Quote[]>([]); // Array of `Quote` objects
    const [loading, setLoading] = useState<boolean>(true); // Loading state
    const [liking, setLiking] = useState<boolean>(false); // Liking state
    const [signingIn, setSigningIn] = useState<boolean>(false);
    const [showProfile, setShowProfile] = useState<boolean>(false);
    const [userEmail, setUserEmail] = useState<string>('');
    const [displayUsername, setDisplayUsername] = useState<string>('');
    const indexRef = useRef<number>(0); // Reference to the current quote index
    const favouritesRef = useRef<FavouritesComponentHandle>(null);

    useEffect(() => {
        fetchFirstQuote(); // Called twice in StrictMode (only in development)
    }, []);

    // Close login screen when user becomes authenticated
    useEffect(() => {
        if (isAuthenticated && signingIn) {
            setSigningIn(false);
        }
    }, [isAuthenticated, signingIn]);

    // Log user when authenticated and fetch email and preferred username
    useEffect(() => {
        const fetchUserAttributes = async () => {
            if (isAuthenticated && user) {
                console.log('User authenticated:', user);
                try {
                    const { fetchUserAttributes } = await import('aws-amplify/auth');
                    const attributes = await fetchUserAttributes();
                    console.log('User attributes:', attributes);
                    setUserEmail(attributes.email || 'No email found');
                    
                    // Use preferred_username if available, otherwise use the technical username
                    const preferredUsername = attributes.preferred_username;
                    if (preferredUsername && preferredUsername !== attributes.email) {
                        // User has set a custom username
                        setDisplayUsername(preferredUsername);
                    } else {
                        // Use the Cognito username (might be Google_xxx)
                        setDisplayUsername(user.username);
                    }
                } catch (error) {
                    console.error('Failed to fetch user attributes:', error);
                    setUserEmail('Error loading email');
                    setDisplayUsername(user.username);
                }
            } else {
                setUserEmail('');
                setDisplayUsername('');
            }
        };
        fetchUserAttributes();
    }, [isAuthenticated, user]);

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

    const signIn = (): void => {
        setSigningIn(!signingIn);
    };

    const toggleProfile = (): void => {
        setShowProfile(!showProfile);
    };

    const handleSignOut = async (): Promise<void> => {
        await signOut();
        setShowProfile(false);
    };

    const closeProfile = (): void => {
        setShowProfile(false);
    };

    return (
        <div className="app">
            <div className={`quoteView ${(signingIn || showProfile || needsUsernameSetup) ? 'fullHeight fullWidth' : ''}`}>
                {showProfile && isAuthenticated && user ? (
                    <div className="profile">
                        <h2>User Profile</h2>
                        <div className="profile-info">
                            <p><strong>Username:</strong> {displayUsername || user.username}</p>
                            <p><strong>Email:</strong> {userEmail || 'Loading...'}</p>
                            <p><strong>Roles:</strong> {userGroups.length > 0 ? userGroups.join(', ') : 'No roles assigned'}</p>
                        </div>
                        <div className="profile-actions">
                            <button className="signOutButton" onClick={handleSignOut}>
                                Sign Out
                            </button>
                            <button className="cancelButton" onClick={closeProfile}>
                                Cancel
                            </button>
                        </div>
                    </div>
                ) : needsUsernameSetup && !showProfile ? (
                    <Login />
                ) : signingIn && !isAuthenticated ? (
                    <Login onCancel={() => setSigningIn(false)} />
                ) : (
                    <>
                        <p>
                            "{loading ? "Loading..." : quote?.quoteText || ""}"
                        </p>
                        <p className="author">
                            {loading ? "" : quote?.author || ""}
                        </p>
                    </>
                )}
            </div>
            <div className={`buttonBar ${(signingIn || showProfile || needsUsernameSetup) ? 'hideOnNarrow' : ''}`}>
                <div className="logo">
                    <div className="logo-header">CODE-BULTER</div>
                    <div className="logo-main">Quote</div>
                </div>
                {isAuthenticated && user && (
                    <div className="userInitial" title={displayUsername || user.username} onClick={toggleProfile} style={{ cursor: 'pointer' }}>
                        {(displayUsername || user.username).charAt(0).toUpperCase()}
                    </div>
                )}
                <button className="newQuoteButton" disabled={loading || signingIn || showProfile || needsUsernameSetup} onClick={newQuote}>
                    {loading ? "Loading..." : "New Quote"}
                </button>
                <button 
                    className="likeButton" 
                    disabled={!isAuthenticated || !hasRole('USER') || liking || !!quote?.liked || signingIn || showProfile || needsUsernameSetup} 
                    onClick={like}
                    title={!isAuthenticated ? "Sign in to like quotes" : !hasRole('USER') ? "USER role required to like quotes" : ""}
                >
                    {liking ? "Liking..." : "Like"}
                </button>
                <button className="previousButton" disabled={indexRef.current === 0 || signingIn || showProfile || needsUsernameSetup} onClick={previous}>
                    Previous
                </button>
                <button
                    className="nextButton"
                    disabled={indexRef.current >= receivedQuotes.length - 1 || signingIn || showProfile || needsUsernameSetup}
                    onClick={next}
                >
                    Next
                </button>
                <button className="firstButton" disabled={signingIn || showProfile || needsUsernameSetup} onClick={jumpToFirst}>
                    First
                </button>
                <button className="lastButton" disabled={signingIn || showProfile || needsUsernameSetup} onClick={jumpToLast}>
                    Last
                </button>
                {!isAuthenticated ? (
                    <button className="signinButton" disabled={isLoading || signingIn || showProfile} onClick={isAuthenticated ? signOut : signIn}>
                        {isLoading ? "Loading..." : (isAuthenticated ? "Sign Out" : "Sign In")}
                    </button>
                ) : ""}
            </div>
            {!signingIn && !showProfile && !needsUsernameSetup && (
                <FavouritesComponent ref={favouritesRef}/>
            )}
        </div>
    );
};

export default App;