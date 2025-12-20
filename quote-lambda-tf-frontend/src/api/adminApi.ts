import { BASE_URL } from "../constants/constants";
import { fetchAuthSession } from 'aws-amplify/auth';

async function getAuthHeaders(): Promise<HeadersInit> {
    try {
        const session = await fetchAuthSession();
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

export interface UserInfo {
    username: string;
    email: string;
    groups: string[];
    enabled: boolean;
    userStatus: string;
    userCreateDate?: string;
    userLastModifiedDate?: string;
}

export interface QuoteWithLikeCount {
    id: number;
    quoteText: string;
    author: string;
    likeCount: number;
}

export interface QuotePageResponse {
    quotes: QuoteWithLikeCount[];
    totalCount: number;
    page: number;
    pageSize: number;
    totalPages: number;
}

export interface QuoteAddResponse {
    quotesAdded: number;
    totalQuotes: number;
    message: string;
}

async function listUsers(): Promise<UserInfo[]> {
    const authHeaders = await getAuthHeaders();
    const response = await fetch(`${BASE_URL}/admin/users`, {
        method: "GET",
        headers: {
            ...authHeaders,
        },
    });
    
    if (!response.ok) {
        throw new Error(`Failed to fetch users: ${response.status} ${response.statusText}`);
    }
    
    return await response.json();
}

async function addUserToGroup(username: string, groupName: string): Promise<void> {
    const authHeaders = await getAuthHeaders();
    const response = await fetch(`${BASE_URL}/admin/users/${encodeURIComponent(username)}/groups/${groupName}`, {
        method: "POST",
        headers: {
            ...authHeaders,
        },
    });
    
    if (!response.ok) {
        throw new Error(`Failed to add user to group: ${response.status} ${response.statusText}`);
    }
}

async function removeUserFromGroup(username: string, groupName: string): Promise<void> {
    const authHeaders = await getAuthHeaders();
    const response = await fetch(`${BASE_URL}/admin/users/${encodeURIComponent(username)}/groups/${groupName}`, {
        method: "DELETE",
        headers: {
            ...authHeaders,
        },
    });
    
    if (!response.ok) {
        throw new Error(`Failed to remove user from group: ${response.status} ${response.statusText}`);
    }
}

async function deleteUser(username: string): Promise<void> {
    const authHeaders = await getAuthHeaders();
    const response = await fetch(`${BASE_URL}/admin/users/${encodeURIComponent(username)}`, {
        method: "DELETE",
        headers: {
            ...authHeaders,
        },
    });
    
    if (!response.ok) {
        const errorText = await response.text();
        console.error('Delete user failed:', response.status, errorText);
        throw new Error(`Failed to delete user: ${response.status} - ${errorText}`);
    }
}

async function getQuotes(
    page: number = 1,
    pageSize: number = 50,
    quoteText?: string,
    author?: string,
    sortBy?: string,
    sortOrder?: string
): Promise<QuotePageResponse> {
    const authHeaders = await getAuthHeaders();
    const params = new URLSearchParams();
    params.append('page', page.toString());
    params.append('pageSize', pageSize.toString());
    if (quoteText) params.append('quoteText', quoteText);
    if (author) params.append('author', author);
    if (sortBy) params.append('sortBy', sortBy);
    if (sortOrder) params.append('sortOrder', sortOrder);

    const response = await fetch(`${BASE_URL}/admin/quotes?${params.toString()}`, {
        method: "GET",
        headers: {
            ...authHeaders,
        },
    });
    
    if (!response.ok) {
        const errorText = await response.text();
        console.error('Get quotes failed:', response.status, errorText);
        throw new Error(`Failed to fetch quotes: ${response.status} - ${errorText}`);
    }
    
    return await response.json();
}

async function fetchAndAddNewQuotes(): Promise<QuoteAddResponse> {
    const authHeaders = await getAuthHeaders();
    const response = await fetch(`${BASE_URL}/admin/quotes/fetch`, {
        method: "POST",
        headers: {
            ...authHeaders,
        },
    });
    
    if (!response.ok) {
        const errorText = await response.text();
        console.error('Fetch quotes failed:', response.status, errorText);
        throw new Error(`Failed to add quotes: ${response.status} - ${errorText}`);
    }
    
    return await response.json();
}

async function getTotalLikes(): Promise<{ totalLikes: number }> {
    const authHeaders = await getAuthHeaders();
    const response = await fetch(`${BASE_URL}/admin/likes/total`, {
        method: "GET",
        headers: {
            ...authHeaders,
        },
    });
    
    if (!response.ok) {
        const errorText = await response.text();
        console.error('Get total likes failed:', response.status, errorText);
        throw new Error(`Failed to fetch total likes: ${response.status} - ${errorText}`);
    }
    
    return await response.json();
}

export default {
    listUsers,
    addUserToGroup,
    removeUserFromGroup,
    deleteUser,
    getQuotes,
    fetchAndAddNewQuotes,
    getTotalLikes,
};
