import { GoogleLogin } from '@react-oauth/google';

const CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID;

export default function GoogleSignInButton({ onCredential, onError, disabled }) {
  // Without a client ID the button simply isn't shown
  if (!CLIENT_ID) return null;

  return (
    <div className={`google-button${disabled ? ' is-disabled' : ''}`}>
      <GoogleLogin
        onSuccess={(response) => {
          if (response.credential) {
            onCredential(response.credential);
          } else {
            onError('Google did not return a sign-in token. Please try again.');
          }
        }}
        onError={() => onError('Google sign-in was cancelled or failed. Please try again.')}
        text="continue_with"
        shape="rectangular"
        width="320"
      />
    </div>
  );
}