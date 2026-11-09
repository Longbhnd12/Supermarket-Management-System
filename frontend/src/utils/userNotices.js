// UC05 - message shown on the Users List after "Create User".
// The backend emails the username + temporary password to the new account and tells us
// whether that worked (credentialsEmailSent). The account is created either way.
export function createdUserNotice(createdUser) {
    const email = createdUser?.email;
    if (createdUser?.credentialsEmailSent === true) {
        return {
            notice: `Added successfully. The username and temporary password were sent to ${email}.`,
            noticeType: 'success'
        };
    }
    if (createdUser?.credentialsEmailSent === false) {
        return {
            notice: `Added successfully, but the login details email to ${email} could not be sent. `
                + 'Please give the username and temporary password to the user directly.',
            noticeType: 'warning'
        };
    }
    // Older backend without the email feature
    return { notice: 'Added successfully.', noticeType: 'success' };
}
