# Assumptions

In this file I will clarify and explain some of the decision I've made that was not clear for the assignment pdf.

## Project Decisions

1. **Seeded admin replaces public user creation** - Only login is public. The application creates a default ADMIN user on startup if it does not already exist, so the first login can use this admin account and then create the rest of the users through the protected User API.

2. **One password for everyone** - we don't recieve password for user, so I've decided that everyone login, will need to use the same password "secret", it possible to configure different password, explained at run.md.

3. **Cascading delete made by SYSTEM** - in case for example that project deleted with active tickets and comments. tickets and comments will be deleted by the SYSTEM. That's for audit inspection.

4. **Two users try to change ticket/comment together** - the architecture allows only one user to change ticket/comment. The first one will acquire the lock for update, while the other trying to update, will recieve exception so they can read the update, before trying to change it by themselves. 