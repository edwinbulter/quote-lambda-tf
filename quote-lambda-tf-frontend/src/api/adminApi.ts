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

export default {
    listUsers,
    addUserToGroup,
    removeUserFromGroup,
};
