function connectCareChainRealtime(subscriptions) {
    if (!Array.isArray(subscriptions) || subscriptions.length === 0) {
        return;
    }

    if (typeof SockJS === 'undefined' || typeof Stomp === 'undefined') {
        console.warn('Realtime libraries are unavailable.');
        return;
    }

    let client = null;
    let reconnectTimer = null;

    const connect = () => {
        client = Stomp.over(new SockJS('/ws'));
        client.debug = null;

        client.connect({}, () => {
            subscriptions.forEach(subscription => {
                client.subscribe(subscription.destination, frame => {
                    let payload = {};
                    try {
                        payload = frame.body ? JSON.parse(frame.body) : {};
                    } catch (error) {
                        console.error('Failed to parse realtime payload', error);
                    }
                    subscription.handler(payload);
                });
            });
        }, () => {
            if (reconnectTimer) {
                clearTimeout(reconnectTimer);
            }
            reconnectTimer = setTimeout(connect, 5000);
        });
    };

    connect();

    window.addEventListener('beforeunload', () => {
        if (reconnectTimer) {
            clearTimeout(reconnectTimer);
        }
        if (client && client.connected) {
            client.disconnect(() => {});
        }
    });
}
