UPDATE projects
SET description = 'No description provided'
WHERE description IS NULL;

ALTER TABLE projects
ALTER COLUMN description SET NOT NULL;
