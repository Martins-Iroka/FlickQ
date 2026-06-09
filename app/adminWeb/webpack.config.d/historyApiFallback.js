// Serve index.html for unknown paths so that deep links / reloads on routes like
// /dashboard or /reservations/42 resolve to the SPA instead of returning a dev-server 404.
// Required because bindToNavigation (see AdminApp.kt) writes real paths to the address bar.
config.devServer = config.devServer || {};
config.devServer.historyApiFallback = true;
