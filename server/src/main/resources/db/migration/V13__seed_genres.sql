-- Baseline genre catalogue so movies can be tagged on a fresh database (the genres table is
-- otherwise empty until an admin adds rows). This is reference data needed in every environment.
-- Idempotent: ON CONFLICT keeps re-runs and any later admin edits/removals safe.
INSERT INTO genres (name) VALUES
    ('Action'),
    ('Adventure'),
    ('Animation'),
    ('Comedy'),
    ('Crime'),
    ('Documentary'),
    ('Drama'),
    ('Fantasy'),
    ('Horror'),
    ('Mystery'),
    ('Romance'),
    ('Sci-Fi'),
    ('Thriller')
ON CONFLICT (name) DO NOTHING;
