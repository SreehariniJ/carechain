function getStoredUser() {
    const raw = sessionStorage.getItem('carechainUser');
    if (!raw) {
        return null;
    }

    try {
        return JSON.parse(raw);
    } catch (error) {
        sessionStorage.removeItem('carechainUser');
        return null;
    }
}

function storeUser(user) {
    sessionStorage.setItem('carechainUser', JSON.stringify(user));
}

async function apiFetch(url, options = {}) {
    const settings = { ...options, credentials: 'same-origin' };
    settings.headers = new Headers(options.headers || {});

    const method = (settings.method || 'GET').toUpperCase();
    settings.method = method;

    if ((method === 'POST' || method === 'PUT') && settings.body == null) {
        settings.body = '{}';
    }

    if (settings.body && !(settings.body instanceof FormData) && !settings.headers.has('Content-Type')) {
        settings.headers.set('Content-Type', 'application/json');
    }

    const response = await fetch(url, settings);
    if (response.status === 401 && !window.location.pathname.startsWith('/login')) {
        sessionStorage.removeItem('carechainUser');
        window.location.href = '/login';
    }
    return response;
}

async function initAuthenticatedPage(emailElementId) {
    const response = await apiFetch('/api/auth/me');
    if (!response.ok) {
        return null;
    }

    const user = await response.json();
    storeUser(user);

    if (emailElementId) {
        const element = document.getElementById(emailElementId);
        if (element) {
            element.textContent = user.email || '';
        }
    }

    return user;
}

async function logout() {
    try {
        await apiFetch('/api/auth/logout', { method: 'POST' });
    } finally {
        sessionStorage.removeItem('carechainUser');
        window.location.href = '/login';
    }
}
