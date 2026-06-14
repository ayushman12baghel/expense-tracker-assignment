import { useState, useEffect } from 'react';
import api from '../services/api';
import Navbar from '../components/Navbar';
import CreateGroupModal from '../components/modals/CreateGroupModal';
import { useNavigate } from 'react-router-dom';

export default function Dashboard() {
  const [groups, setGroups] = useState([]);
  const [loading, setLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    fetchGroups();
  }, []);

  const fetchGroups = async () => {
    try {
      const res = await api.get('/api/groups');
      setGroups(res.data);
    } catch (err) {
      console.error('Failed to fetch groups', err);
    } finally {
      setLoading(false);
    }
  };

  const handleGroupCreated = (newGroup) => {
    setGroups([...groups, newGroup]);
  };

  return (
    <div className="min-h-screen bg-gray-50">
      <Navbar />
      
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="md:flex md:items-center md:justify-between mb-8">
          <div className="flex-1 min-w-0">
            <h2 className="text-2xl font-bold leading-7 text-gray-900 sm:text-3xl sm:truncate">
              Dashboard
            </h2>
          </div>
          <div className="mt-4 flex md:mt-0 md:ml-4">
            <button
              onClick={() => setIsModalOpen(true)}
              className="ml-3 inline-flex items-center px-4 py-2 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-emerald-600 hover:bg-emerald-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-emerald-500 transition-colors"
            >
              + Create Group
            </button>
          </div>
        </div>

        {/* Since global cross-group balances require joining all group balances, we keep it simple here and focus on the groups list as requested */}

        <div className="bg-white shadow overflow-hidden sm:rounded-md border border-gray-100">
          <ul className="divide-y divide-gray-200">
            {loading ? (
              <li className="px-6 py-8 text-center text-gray-500">Loading your groups...</li>
            ) : groups.length === 0 ? (
              <li className="px-6 py-12 text-center">
                <p className="text-gray-500 mb-4">You are not part of any groups yet.</p>
                <button
                  onClick={() => setIsModalOpen(true)}
                  className="inline-flex items-center px-4 py-2 border border-emerald-300 rounded-md shadow-sm text-sm font-medium text-emerald-700 bg-emerald-50 hover:bg-emerald-100 focus:outline-none"
                >
                  Create your first group
                </button>
              </li>
            ) : (
              groups.map((group) => (
                <li key={group.id}>
                  <div 
                    onClick={() => navigate(`/groups/${group.id}`)}
                    className="block hover:bg-emerald-50 transition-colors cursor-pointer"
                  >
                    <div className="px-4 py-4 sm:px-6 flex items-center justify-between">
                      <div className="flex items-center">
                        <div className="flex-shrink-0 h-10 w-10 bg-emerald-100 rounded-full flex items-center justify-center border border-emerald-200">
                          <span className="text-emerald-700 font-bold text-lg">
                            {group.name.charAt(0).toUpperCase()}
                          </span>
                        </div>
                        <div className="ml-4">
                          <p className="text-sm font-medium text-emerald-600 truncate">{group.name}</p>
                          <p className="text-sm text-gray-500">
                            {group.members?.length || 0} members
                          </p>
                        </div>
                      </div>
                      <div className="flex items-center">
                        <svg className="h-5 w-5 text-gray-400" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                          <path fillRule="evenodd" d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z" clipRule="evenodd" />
                        </svg>
                      </div>
                    </div>
                  </div>
                </li>
              ))
            )}
          </ul>
        </div>
      </main>

      <CreateGroupModal 
        isOpen={isModalOpen} 
        onClose={() => setIsModalOpen(false)} 
        onGroupCreated={handleGroupCreated}
      />
    </div>
  );
}
