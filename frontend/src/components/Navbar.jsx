import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';

export default function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/auth');
  };

  return (
    <nav className="bg-emerald-900 shadow-md">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between h-16">
          <div className="flex items-center">
            <div 
              className="flex-shrink-0 flex items-center cursor-pointer"
              onClick={() => navigate('/dashboard')}
            >
              <span className="text-white text-xl font-bold tracking-wider">Splitwise</span>
            </div>
          </div>
          <div className="flex items-center">
            <span className="text-emerald-100 mr-4 text-sm font-medium">
              {user?.name}
            </span>
            <button
              onClick={handleLogout}
              className="bg-emerald-800 hover:bg-emerald-700 text-white px-3 py-1.5 rounded-md text-sm font-medium transition-colors"
            >
              Logout
            </button>
          </div>
        </div>
      </div>
    </nav>
  );
}
