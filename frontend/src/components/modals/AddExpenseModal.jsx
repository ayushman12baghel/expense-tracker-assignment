import { useState, useEffect } from 'react';
import api from '../../services/api';

export default function AddExpenseModal({ isOpen, onClose, groupId, members, currentUser, onExpenseAdded }) {
  const [description, setDescription] = useState('');
  const [amount, setAmount] = useState('');
  const [payerId, setPayerId] = useState(currentUser?.id || '');
  const [splitType, setSplitType] = useState('EQUAL');
  
  // State for tracking values when not EQUAL
  const [splitValues, setSplitValues] = useState({});
  // State for EQUAL checkboxes
  const [includedMembers, setIncludedMembers] = useState({});
  
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // Initialize states
  useEffect(() => {
    if (members?.length > 0) {
      const initialIncluded = {};
      const initialValues = {};
      members.forEach(m => {
        initialIncluded[m.id] = true;
        initialValues[m.id] = '';
      });
      setIncludedMembers(initialIncluded);
      setSplitValues(initialValues);
      
      if (!payerId) setPayerId(members[0].id);
    }
  }, [members, isOpen, payerId]);

  if (!isOpen) return null;

  const handleValueChange = (userId, value) => {
    setSplitValues(prev => ({ ...prev, [userId]: value }));
  };

  const handleCheckboxChange = (userId) => {
    setIncludedMembers(prev => ({ ...prev, [userId]: !prev[userId] }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    try {
      let splits = [];
      
      if (splitType === 'EQUAL') {
        const included = Object.keys(includedMembers).filter(id => includedMembers[id]);
        if (included.length === 0) throw new Error("At least one person must be included in the split");
        splits = included.map(id => ({ userId: id, value: null }));
      } else {
        splits = Object.entries(splitValues)
          .filter(([_, val]) => val !== '' && Number(val) > 0)
          .map(([id, val]) => ({ userId: id, value: Number(val) }));
          
        if (splits.length === 0) throw new Error("Please enter split values");
      }

      const payload = {
        description,
        amount: Number(amount),
        payerId,
        date: new Date().toISOString().split('T')[0],
        splitType,
        splits
      };

      const res = await api.post(`/api/groups/${groupId}/expenses`, payload);
      onExpenseAdded(res.data);
      
      // Reset form
      setDescription('');
      setAmount('');
      setSplitType('EQUAL');
      onClose();
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to add expense');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto">
      <div className="flex items-end justify-center min-h-screen pt-4 px-4 pb-20 text-center sm:block sm:p-0">
        <div className="fixed inset-0 transition-opacity" onClick={onClose}>
          <div className="absolute inset-0 bg-gray-900 opacity-75 backdrop-blur-sm"></div>
        </div>
        <span className="hidden sm:inline-block sm:align-middle sm:h-screen">&#8203;</span>

        <div className="inline-block align-bottom bg-white rounded-lg text-left overflow-hidden shadow-xl transform transition-all sm:my-8 sm:align-middle sm:max-w-xl sm:w-full">
          <form onSubmit={handleSubmit}>
            <div className="bg-white px-4 pt-5 pb-4 sm:p-6 sm:pb-4 border-b">
              <div className="flex justify-between items-center mb-5">
                <h3 className="text-xl font-bold text-gray-900">Add an expense</h3>
                <button type="button" onClick={onClose} className="text-gray-400 hover:text-gray-500">
                  <span className="text-2xl">&times;</span>
                </button>
              </div>

              <div className="space-y-4">
                <div className="flex space-x-4">
                  <div className="flex-1">
                    <label className="block text-sm font-medium text-gray-700">Description</label>
                    <input
                      type="text"
                      required
                      value={description}
                      onChange={(e) => setDescription(e.target.value)}
                      className="mt-1 block w-full border border-gray-300 rounded-md shadow-sm py-2 px-3 focus:ring-emerald-500 focus:border-emerald-500 sm:text-sm"
                      placeholder="Dinner, Uber, Groceries..."
                    />
                  </div>
                  <div className="w-1/3">
                    <label className="block text-sm font-medium text-gray-700">Amount</label>
                    <div className="mt-1 relative rounded-md shadow-sm">
                      <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                        <span className="text-gray-500 sm:text-sm">₹</span>
                      </div>
                      <input
                        type="number"
                        step="0.01"
                        min="0.01"
                        required
                        value={amount}
                        onChange={(e) => setAmount(e.target.value)}
                        className="block w-full pl-7 border border-gray-300 rounded-md py-2 px-3 focus:ring-emerald-500 focus:border-emerald-500 sm:text-sm"
                        placeholder="0.00"
                      />
                    </div>
                  </div>
                </div>

                <div className="flex space-x-4 items-center bg-gray-50 p-3 rounded-lg border border-gray-100">
                  <div className="whitespace-nowrap text-sm text-gray-600">Paid by</div>
                  <select
                    value={payerId}
                    onChange={(e) => setPayerId(e.target.value)}
                    className="block w-full border-gray-300 rounded-md shadow-sm focus:ring-emerald-500 focus:border-emerald-500 sm:text-sm"
                  >
                    {members.map(m => (
                      <option key={m.id} value={m.id}>{m.id === currentUser?.id ? 'You' : m.name}</option>
                    ))}
                  </select>
                  <div className="whitespace-nowrap text-sm text-gray-600">and split</div>
                  <select
                    value={splitType}
                    onChange={(e) => setSplitType(e.target.value)}
                    className="block w-full border-gray-300 rounded-md shadow-sm focus:ring-emerald-500 focus:border-emerald-500 sm:text-sm font-medium text-emerald-700"
                  >
                    <option value="EQUAL">equally</option>
                    <option value="UNEQUAL">unequally (exact amounts)</option>
                    <option value="PERCENTAGE">by percentages</option>
                    <option value="SHARE">by shares</option>
                  </select>
                </div>

                {/* Dynamic Split Area */}
                <div className="mt-4 border border-gray-200 rounded-lg overflow-hidden">
                  <div className="bg-gray-50 px-4 py-2 border-b text-xs font-semibold text-gray-500 uppercase tracking-wider">
                    {splitType === 'EQUAL' ? 'Choose who to split with' : 'Enter split values'}
                  </div>
                  <ul className="divide-y divide-gray-200 max-h-48 overflow-y-auto">
                    {members.map(m => (
                      <li key={m.id} className="px-4 py-3 flex items-center justify-between hover:bg-gray-50">
                        <div className="flex items-center">
                          {splitType === 'EQUAL' && (
                            <input
                              type="checkbox"
                              checked={includedMembers[m.id] || false}
                              onChange={() => handleCheckboxChange(m.id)}
                              className="h-4 w-4 text-emerald-600 focus:ring-emerald-500 border-gray-300 rounded mr-3"
                            />
                          )}
                          <span className={`text-sm font-medium ${splitType === 'EQUAL' && !includedMembers[m.id] ? 'text-gray-400 line-through' : 'text-gray-900'}`}>
                            {m.name}
                          </span>
                        </div>
                        {splitType !== 'EQUAL' && (
                          <div className="relative">
                            {splitType === 'UNEQUAL' && <span className="absolute left-3 top-1.5 text-gray-400">₹</span>}
                            <input
                              type="number"
                              step="0.01"
                              min="0"
                              placeholder="0"
                              value={splitValues[m.id] || ''}
                              onChange={(e) => handleValueChange(m.id, e.target.value)}
                              className={`block w-24 border-gray-300 rounded-md shadow-sm focus:ring-emerald-500 focus:border-emerald-500 sm:text-sm text-right ${splitType === 'UNEQUAL' ? 'pl-6' : ''}`}
                            />
                            {splitType === 'PERCENTAGE' && <span className="absolute right-3 top-1.5 text-gray-400">%</span>}
                            {splitType === 'SHARE' && <span className="absolute -right-10 top-1.5 text-gray-400 text-xs">shares</span>}
                          </div>
                        )}
                      </li>
                    ))}
                  </ul>
                </div>

                {error && <p className="text-sm text-red-600 bg-red-50 p-2 rounded">{error}</p>}
              </div>
            </div>
            
            <div className="bg-gray-50 px-4 py-3 sm:px-6 flex justify-end space-x-3">
              <button
                type="button"
                onClick={onClose}
                className="inline-flex justify-center rounded-md border border-gray-300 shadow-sm px-4 py-2 bg-white text-base font-medium text-gray-700 hover:bg-gray-50 focus:outline-none sm:text-sm"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={loading || !amount}
                className="inline-flex justify-center rounded-md border border-transparent shadow-sm px-6 py-2 bg-emerald-600 text-base font-medium text-white hover:bg-emerald-700 focus:outline-none sm:text-sm disabled:opacity-50"
              >
                {loading ? 'Saving...' : 'Save'}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}
