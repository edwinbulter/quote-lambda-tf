import './App.scss';
import React, { useEffect, useRef, useState } from "react";
import quoteApi from "./api/quoteApi";
import FavouritesComponent, { FavouritesComponentHandle } from "./components/FavouritesComponent.tsx";

// Import `Quote` interface from the appropriate file
import { Quote } from "./types/Quote"; // Adjust the path based on your project
import { useAuth } from './contexts/AuthContext';
import { Login } from './components/Login';
import { ManagementScreen } from './components/ManagementScreen';
import { ManageFavouritesScreen } from './components/ManageFavouritesScreen';
import { ViewedQuotesScreen } from './components/ViewedQuotesScreen';
import { UserManagementScreen } from './components/UserManagementScreen';
import { QuoteManagementScreen } from './components/QuoteManagementScreen';

const App: React.FC = () => {
    const { isAuthenticated, isLoading, signOut, user, hasRole, userGroups, needsUsernameSetup } = useAuth();
    const [quote, setQuote] = useState<Quote | null>(null); // Allow `null` for initial state
    const [receivedQuotes, setReceivedQuotes] = useState<Quote[]>([]); // Array of `Quote` objects (for unauthenticated users)
    const [currentQuoteId, setCurrentQuoteId] = useState<number | null>(null); // Current quote ID for sequential navigation
    const [lastQuoteId, setLastQuoteId] = useState<number>(0); // User's last viewed quote ID
    const [loading, setLoading] = useState<boolean>(true); // Loading state
    const [liking, setLiking] = useState<boolean>(false); // Liking state
    const [signingIn, setSigningIn] = useState<boolean>(false);
    const [showProfile, setShowProfile] = useState<boolean>(false);
    const [showManagement, setShowManagement] = useState<boolean>(false);
    const [managementView, setManagementView] = useState<'main' | 'favourites' | 'viewed' | 'users' | 'quotes'>('main');
    const [userEmail, setUserEmail] = useState<string>('');
    const [displayUsername, setDisplayUsername] = useState<string>('');
    const favouritesRef = useRef<FavouritesComponentHandle>(null);

    useEffect(() => {
        fetchFirstQuote(); // Called twice in StrictMode (only in development)
    }, []);

    // Load user progress when user authenticates
    useEffect(() => {
        const loadUserProgress = async () => {
            if (isAuthenticated && user) {
                try {
                    console.log('Loading user progress for authenticated user...');
                    const progress = await quoteApi.getUserProgress();
                    setLastQuoteId(progress.lastQuoteId);
                    
                    if (progress.lastQuoteId > 0) {
                        // Set current quote to last viewed quote
                        const lastQuote = await quoteApi.getQuoteById(progress.lastQuoteId);
                        setQuote(lastQuote);
                        setCurrentQuoteId(lastQuote.id);
                    }
                    console.log(`Loaded user progress: lastQuoteId=${progress.lastQuoteId}`);
                } catch (error) {
                    console.error('Failed to load user progress:', error);
                    // Fallback to getting next quote
                    fetchNextQuote();
                }
            } else {
                // Clear progress when user signs out
                setCurrentQuoteId(null);
                setLastQuoteId(0);
            }
        };
        loadUserProgress();
    }, [isAuthenticated, user]);

    // Set document title based on environment
    useEffect(() => {
        const hostname = window.location.hostname;
        const isLocalhost = hostname === 'localhost' || hostname === '127.0.0.1';
        const isDevCloudFront = hostname === 'd1fzgis91zws1k.cloudfront.net';
        
        if (isLocalhost) {
            document.title = 'Quote (local)';
        } else if (isDevCloudFront) {
            document.title = 'Quote (dev)';
        } else {
            document.title = 'Quote';
        }
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
    }, [isAuthenticated, user, needsUsernameSetup]);

    const fetchFirstQuote = async (): Promise<void> => {
        try {
            setLoading(true);
            if (isAuthenticated) {
                // For authenticated users, get next sequential quote
                await fetchNextQuote();
            } else {
                // For unauthenticated users, use random quote
                const firstQuote = await quoteApi.getQuote();
                setQuote(firstQuote);
                setReceivedQuotes([firstQuote]);
            }
        } catch (error) {
            console.error('Failed to fetch first quote:', error);
        } finally {
            setLoading(false);
        }
    };

    const fetchNextQuote = async (): Promise<void> => {
        try {
            setLoading(true);
            if (isAuthenticated) {
                // For authenticated users, get next sequential quote
                const nextQuote = await quoteApi.getAuthenticatedQuote();
                setQuote(nextQuote);
                setCurrentQuoteId(nextQuote.id);
                setLastQuoteId(nextQuote.id);
            } else {
                // For unauthenticated users, use random quote
                const uniqueQuote = await quoteApi.getUniqueQuote(receivedQuotes);
                setQuote(uniqueQuote);
                setReceivedQuotes((prevQuotes) => [...prevQuotes, uniqueQuote]);
            }
        } catch (error) {
            console.error('Failed to fetch next quote:', error);
        } finally {
            setLoading(false);
        }
    };

    const newQuote = async (): Promise<void> => {
        await fetchNextQuote();
    };

    const like = async (): Promise<void> => {
        try {
            if (quote && !quote.liked) {
                setLiking(true);
                const updatedQuote = await quoteApi.likeQuote(quote);
                if (updatedQuote && updatedQuote.id === quote.id) {
                    // Update the `liked` property in local state
                    if (!isAuthenticated) {
                        setReceivedQuotes((prevQuotes: Quote[]) =>
                            prevQuotes.map((item: Quote) =>
                                item.id === quote.id ? { ...item, liked: true } : item
                            )
                        );
                    }
                    setQuote({ ...quote, liked: true });
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

    const previous = async (): Promise<void> => {
        if (isAuthenticated && currentQuoteId && currentQuoteId > 1) {
            try {
                setLoading(true);
                const prevQuote = await quoteApi.getPreviousQuote(currentQuoteId);
                setQuote(prevQuote);
                setCurrentQuoteId(prevQuote.id);
            } catch (error) {
                console.error('Failed to fetch previous quote:', error);
            } finally {
                setLoading(false);
            }
        } else if (!isAuthenticated) {
            // For unauthenticated users, use old array-based navigation
            if (receivedQuotes.length > 0) {
                const currentIndex = receivedQuotes.findIndex(q => q.id === quote?.id);
                if (currentIndex > 0) {
                    setQuote(receivedQuotes[currentIndex - 1]);
                }
            }
        }
    };

    const next = async (): Promise<void> => {
        if (isAuthenticated && currentQuoteId && currentQuoteId < lastQuoteId) {
            try {
                setLoading(true);
                const nextQuote = await quoteApi.getNextQuote(currentQuoteId);
                setQuote(nextQuote);
                setCurrentQuoteId(nextQuote.id);
            } catch (error) {
                console.error('Failed to fetch next quote:', error);
            } finally {
                setLoading(false);
            }
        } else if (!isAuthenticated) {
            // For unauthenticated users, use old array-based navigation
            const currentIndex = receivedQuotes.findIndex(q => q.id === quote?.id);
            if (currentIndex >= 0 && currentIndex < receivedQuotes.length - 1) {
                setQuote(receivedQuotes[currentIndex + 1]);
            }
        }
    };

    const jumpToFirst = async (): Promise<void> => {
        if (isAuthenticated) {
            try {
                setLoading(true);
                const firstQuote = await quoteApi.getQuoteById(1);
                setQuote(firstQuote);
                setCurrentQuoteId(firstQuote.id);
            } catch (error) {
                console.error('Failed to fetch first quote:', error);
            } finally {
                setLoading(false);
            }
        } else if (receivedQuotes.length > 0) {
            setQuote(receivedQuotes[0]);
        }
    };

    const jumpToLast = async (): Promise<void> => {
        if (isAuthenticated && lastQuoteId > 0) {
            try {
                setLoading(true);
                const lastQuote = await quoteApi.getQuoteById(lastQuoteId);
                setQuote(lastQuote);
                setCurrentQuoteId(lastQuote.id);
            } catch (error) {
                console.error('Failed to fetch last quote:', error);
            } finally {
                setLoading(false);
            }
        } else if (receivedQuotes.length > 0) {
            setQuote(receivedQuotes[receivedQuotes.length - 1]);
        }
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

    const openManagement = (): void => {
        setShowManagement(true);
        setManagementView('main');
    };

    const closeManagement = (): void => {
        setShowManagement(false);
        setManagementView('main');
        if (favouritesRef.current) {
            favouritesRef.current.reloadFavouriteQuotes();
        }
    };

    return (
        <div className="app">
            <div className={`quoteView ${(signingIn || showProfile || needsUsernameSetup || showManagement) ? 'fullHeight fullWidth' : ''}`}>
                {showManagement ? (
                    managementView === 'main' ? (
                        <ManagementScreen
                            onBack={closeManagement}
                            onNavigateToFavourites={() => setManagementView('favourites')}
                            onNavigateToViewedQuotes={() => setManagementView('viewed')}
                            onNavigateToUserManagement={() => setManagementView('users')}
                            onNavigateToQuoteManagement={() => setManagementView('quotes')}
                            hasUserRole={hasRole('USER')}
                            hasAdminRole={hasRole('ADMIN')}
                        />
                    ) : managementView === 'favourites' ? (
                        <ManageFavouritesScreen
                            onBack={() => setManagementView('main')}
                        />
                    ) : managementView === 'viewed' ? (
                        <ViewedQuotesScreen
                            onBack={() => setManagementView('main')}
                        />
                    ) : managementView === 'users' ? (
                        <UserManagementScreen
                            onBack={() => setManagementView('main')}
                        />
                    ) : (
                        <QuoteManagementScreen
                            onBack={() => setManagementView('main')}
                        />
                    )
                ) : showProfile && isAuthenticated && user ? (
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
                ) : showManagement ? null : (
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
                <button className="newQuoteButton" disabled={loading || signingIn || showProfile || needsUsernameSetup || showManagement} onClick={newQuote}>
                    {loading ? "Loading..." : "New Quote"}
                </button>
                <button 
                    className="likeButton" 
                    disabled={!isAuthenticated || !hasRole('USER') || liking || !!quote?.liked || signingIn || showProfile || needsUsernameSetup || showManagement} 
                    onClick={like}
                    title={!isAuthenticated ? "Sign in to like quotes" : !hasRole('USER') ? "USER role required to like quotes" : ""}
                >
                    {liking ? "Liking..." : "Like"}
                </button>
                <button 
                    className="previousButton" 
                    disabled={(isAuthenticated ? (currentQuoteId === null || currentQuoteId <= 1) : receivedQuotes.findIndex(q => q.id === quote?.id) <= 0) || signingIn || showProfile || needsUsernameSetup || showManagement || loading} 
                    onClick={previous}
                >
                    Previous
                </button>
                <button
                    className="nextButton"
                    disabled={(isAuthenticated ? (currentQuoteId === null || currentQuoteId >= lastQuoteId) : receivedQuotes.findIndex(q => q.id === quote?.id) >= receivedQuotes.length - 1) || signingIn || showProfile || needsUsernameSetup || showManagement || loading}
                    onClick={next}
                >
                    Next
                </button>
                <button className="firstButton" disabled={signingIn || showProfile || needsUsernameSetup || showManagement} onClick={jumpToFirst}>
                    First
                </button>
                <button className="lastButton" disabled={signingIn || showProfile || needsUsernameSetup || showManagement} onClick={jumpToLast}>
                    Last
                </button>
                {isAuthenticated && (
                    <button 
                        className="manageButton" 
                        disabled={signingIn || showProfile || needsUsernameSetup || showManagement}
                        onClick={openManagement}
                    >
                        Manage
                    </button>
                )}
                {!isAuthenticated ? (
                    <button className="signinButton" disabled={isLoading || signingIn || showProfile} onClick={isAuthenticated ? signOut : signIn}>
                        {isLoading ? "Loading..." : (isAuthenticated ? "Sign Out" : "Sign In")}
                    </button>
                ) : ""}
            </div>
            {!signingIn && !showProfile && !needsUsernameSetup && !showManagement && (
                <FavouritesComponent ref={favouritesRef}/>
            )}
        </div>
    );
};

export default App;