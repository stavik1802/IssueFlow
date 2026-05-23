# Assumptions

In this file I will clarify and explain some of the decision I've made that was not clear for the assignment pdf.

## Project Decisions

1. **Creating User is public** - If only login was public. Firstly, there is no way to know the username is part Users, espically in the first login. Secondly, if the user login and he is not in the USERS we don't know if he ADMIN/DEVELOPER. Therefore I decided to make creating user creation public, We have to create user in order to login with this user.

2. **One password for everyone** - we don't recieve password for user, so I've decided that everyone login, will need to use the same password "secret", it possible to configure different password, explained at run.md.

3. **Cascading delete made by SYSTEM** - in case for example that project deleted with active tickets and comments. tickets and comments will be deleted by the SYSTEM. That's for audit inspection.
