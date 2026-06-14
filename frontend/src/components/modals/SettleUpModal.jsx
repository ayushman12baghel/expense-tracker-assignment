import { useState } from 'react';
import api from '../../services/api';

export default function SettleUpModal({ isOpen, onClose, groupId, simplifiedDebts, currentUser, members, onSettlementComplete }) {
  const [selectedDebt, setSelectedDebt] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  if (!isOpen) return null;

  // Find debts where current user owes money (from == currentUser)
  const myDebts = simplifiedDebts.filter(d => d.from === currentUser.id);

  const handleSettle = async (e) => {
    e.preventDefault();
    if (!selectedDebt) return;
    
    setLoading(true);
    setError('');

    try {
      await api.post(`/api/groups/${groupId}/settlements`, {
        payerId: currentUser.id,
        payeeId: selectedDebt.to,
        amount: selectedDebt.amount
      });
      onSettlementComplete();
      setSelectedDebt(null);
      onClose();
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to record settlement');
    } finally {
      setLoading(false);
    }
  };

  const getUserName = (id) => members.find(m => m.id === id)?.name || 'Unknown';

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto">
      <div className="flex items-end justify-center min-h-screen pt-4 px-4 pb-20 text-center sm:block sm:p-0">
        <div className="fixed inset-0 transition-opacity" onClick={onClose}>
          <div className="absolute inset-0 bg-gray-900 opacity-75 backdrop-blur-sm"></div>
        </div>
        <span className="hidden sm:inline-block sm:align-middle sm:h-screen">&#8203;</span>

        <div className="inline-block align-bottom bg-white rounded-lg text-left overflow-hidden shadow-xl transform transition-all sm:my-8 sm:align-middle sm:max-w-md sm:w-full">
          <div className="bg-white px-4 pt-5 pb-4 sm:p-6 sm:pb-4">
            <h3 className="text-lg leading-6 font-medium text-gray-900 mb-4 border-b pb-2">
              Settle Up
            </h3>
            
            {myDebts.length === 0 ? (
              <p className="text-gray-500 text-center py-4">You don't owe anyone anything right now! 🎉</p>
            ) : (
              <form onSubmit={handleSettle} className="space-y-4">
                <p className="text-sm text-gray-600 mb-2">Select a balance to settle:</p>
                <div className="space-y-2 max-h-60 overflow-y-auto">
                  {myDebts.map((debt, idx) => (
                    <label 
                      key={idx} 
                      className={`block border p-3 rounded-md cursor-pointer transition-colors ${selectedDebt === debt ? 'border-emerald-500 bg-emerald-50' : 'border-gray-200 hover:bg-gray-50'}`}
                    >
                      <div className="flex items-center">
                        <input
                          type="radio"
                          name="debt"
                          className="h-4 w-4 text-emerald-600 focus:ring-emerald-500 border-gray-300"
                          checked={selectedDebt === debt}
                          onChange={() => setSelectedDebt(debt)}
                        />
                        <span className="ml-3 block text-sm font-medium text-gray-900">
                          Pay {getUserName(debt.to)} <span className="text-emerald-600 font-bold">₹{debt.amount.toFixed(2)}</span>
                        </span>
                      </div>
                    </label>
                  ))}
                </div>

                {error && <p className="text-sm text-red-600 bg-red-50 p-2 rounded">{error}</p>}
                
                <div className="pt-4 flex flex-row-reverse space-x-2 space-x-reverse">
                  <button
                    type="submit"
                    disabled={!selectedDebt || loading}
                    className="inline-flex justify-center rounded-md border border-transparent shadow-sm px-4 py-2 bg-emerald-600 text-base font-medium text-white hover:bg-emerald-700 focus:outline-none sm:text-sm disabled:opacity-50"
                  >
                    {loading ? 'Recording...' : 'Record Payment'}
                  </button>
                  <button
                    type="button"
                    onClick={onClose}
                    className="inline-flex justify-center rounded-md border border-gray-300 shadow-sm px-4 py-2 bg-white text-base font-medium text-gray-700 hover:bg-gray-50 focus:outline-none sm:text-sm"
                  >
                    Cancel
                  </button>
                </div>
              </form>
            )}
            
            {myDebts.length === 0 && (
              <div className="pt-4 flex justify-end">
                <button onClick={onClose} className="px-4 py-2 bg-gray-100 text-gray-700 rounded-md hover:bg-gray-200">Close</button>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
