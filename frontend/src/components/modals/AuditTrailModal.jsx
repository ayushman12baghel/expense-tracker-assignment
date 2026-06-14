import React, { useState, useEffect } from 'react';
import api from '../../services/api';

const AuditTrailModal = ({ isOpen, onClose, groupId, user1Id, user2Id, user1Name, user2Name, simplifiedAmount }) => {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [auditData, setAuditData] = useState([]);

  useEffect(() => {
    if (isOpen && groupId && user1Id && user2Id) {
      setLoading(true);
      setError('');
      api.get(`/api/groups/${groupId}/balances/audit`, {
        params: {
          user1Id: user1Id,
          user2Id: user2Id
        }
      })
      .then(res => {
        setAuditData(res.data);
      })
      .catch(err => {
        setError(err.response?.data?.error || 'Failed to load audit trail');
      })
      .finally(() => {
        setLoading(false);
      });
    }
  }, [isOpen, groupId, user1Id, user2Id]);

  if (!isOpen) return null;

  // Calculate the raw bilateral net balance from the audit data
  let rawBilateralBalance = 0; // from perspective of user1 paying user2
  // Wait, if user1 owes user2, rawBilateralBalance should be positive or negative?
  // Let's just calculate how much user1 owes user2 and how much user2 owes user1
  let user1OwesUser2 = 0;
  let user2OwesUser1 = 0;

  auditData.forEach(item => {
    if (item.payerId === user2Id && item.borrowerId === user1Id) {
      user1OwesUser2 += item.exactAmountOwed;
    } else if (item.payerId === user1Id && item.borrowerId === user2Id) {
      user2OwesUser1 += item.exactAmountOwed;
    }
  });

  const netDirectDebt = user1OwesUser2 - user2OwesUser1;
  const netDirectOweText = netDirectDebt > 0 
    ? `${user1Name} owes ${user2Name} ₹${netDirectDebt.toFixed(2)}`
    : netDirectDebt < 0 
      ? `${user2Name} owes ${user1Name} ₹${Math.abs(netDirectDebt).toFixed(2)}`
      : 'Directly settled up';

  return (
    <div className="fixed inset-0 bg-gray-500 bg-opacity-75 flex items-center justify-center p-4 z-50">
      <div className="bg-white rounded-lg shadow-xl max-w-3xl w-full max-h-[90vh] flex flex-col overflow-hidden">
        
        <div className="px-6 py-4 border-b border-gray-200">
          <h3 className="text-lg font-medium text-gray-900">
            Audit Trail: {user1Name} & {user2Name}
          </h3>
          <p className="mt-1 text-sm text-gray-500">
            A detailed breakdown of all direct transactions between these two members.
          </p>
        </div>

        <div className="px-6 py-4 overflow-y-auto flex-1">
          {/* Disclaimer about simplified debts */}
          <div className="mb-6 bg-blue-50 border-l-4 border-blue-400 p-4">
            <div className="flex">
              <div className="flex-shrink-0">
                <svg className="h-5 w-5 text-blue-400" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                  <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
                </svg>
              </div>
              <div className="ml-3">
                <p className="text-sm text-blue-700">
                  <strong>Why might this differ from the group balance?</strong><br/>
                  The group is using Debt Simplification. The simplified debt showing <strong>₹{simplifiedAmount?.toFixed(2)}</strong> includes shifted group debts (e.g. if A owes B, and B owes C, simplification shifts it to A owes C). Below are strictly the <em>direct</em> bilateral expenses.
                </p>
              </div>
            </div>
          </div>

          {loading ? (
            <div className="flex justify-center py-8">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-emerald-500"></div>
            </div>
          ) : error ? (
            <div className="text-red-500 text-center py-4">{error}</div>
          ) : auditData.length === 0 ? (
            <p className="text-gray-500 text-center py-8 italic">No direct transactions found between {user1Name} and {user2Name}.</p>
          ) : (
            <>
              <div className="mb-4 flex justify-between items-center bg-gray-50 p-3 rounded border border-gray-200">
                <span className="font-medium text-gray-700">Direct Net Balance:</span>
                <span className={`font-bold ${netDirectDebt !== 0 ? (netDirectDebt > 0 ? 'text-red-600' : 'text-emerald-600') : 'text-gray-600'}`}>
                  {netDirectOweText}
                </span>
              </div>

              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-gray-200">
                  <thead className="bg-gray-50">
                    <tr>
                      <th className="px-3 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Date</th>
                      <th className="px-3 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Description</th>
                      <th className="px-3 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Who Paid</th>
                      <th className="px-3 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Who Borrowed</th>
                      <th className="px-3 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Amount Owed</th>
                    </tr>
                  </thead>
                  <tbody className="bg-white divide-y divide-gray-200">
                    {auditData.map((item, idx) => (
                      <tr key={idx} className="hover:bg-gray-50">
                        <td className="px-3 py-4 whitespace-nowrap text-sm text-gray-500">
                          {new Date(item.date).toLocaleDateString()}
                        </td>
                        <td className="px-3 py-4 text-sm text-gray-900 font-medium">
                          {item.description}
                        </td>
                        <td className="px-3 py-4 whitespace-nowrap text-sm text-gray-500">
                          {item.payerName}
                        </td>
                        <td className="px-3 py-4 whitespace-nowrap text-sm text-gray-500">
                          {item.borrowerName}
                        </td>
                        <td className="px-3 py-4 whitespace-nowrap text-sm font-medium text-right text-red-600">
                          ₹{item.exactAmountOwed.toFixed(2)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </div>

        <div className="px-6 py-4 border-t border-gray-200 bg-gray-50 flex justify-end">
          <button
            onClick={onClose}
            className="px-4 py-2 bg-emerald-600 text-white rounded hover:bg-emerald-700 font-medium transition-colors"
          >
            Close
          </button>
        </div>

      </div>
    </div>
  );
};

export default AuditTrailModal;
