import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import api from '../services/api';
import Navbar from '../components/Navbar';
import AddMemberModal from '../components/modals/AddMemberModal';
import AddExpenseModal from '../components/modals/AddExpenseModal';
import SettleUpModal from '../components/modals/SettleUpModal';
import { useAuth } from '../context/AuthContext';
import ImportReportModal from '../components/modals/ImportReportModal';
import AuditTrailModal from '../components/modals/AuditTrailModal';
import ViewMembersModal from '../components/modals/ViewMembersModal';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

export default function GroupDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  
  const [group, setGroup] = useState(null);
  const [expenses, setExpenses] = useState([]);
  const [balances, setBalances] = useState(null);
  const [activityFeed, setActivityFeed] = useState([]);
  const [loading, setLoading] = useState(true);

  // Modals state
  const [isAddMemberOpen, setIsAddMemberOpen] = useState(false);
  const [isAddExpenseOpen, setIsAddExpenseOpen] = useState(false);
  const [isSettleUpOpen, setIsSettleUpOpen] = useState(false);
  const [isViewMembersOpen, setIsViewMembersOpen] = useState(false);
  const [importReport, setImportReport] = useState(null);
  const [stashedCsvFile, setStashedCsvFile] = useState(null);
  const [selectedDebtForAudit, setSelectedDebtForAudit] = useState(null);
  
  // CSV Upload ref
  const fileInputRef = useRef(null);

  useEffect(() => {
    fetchGroupData();
    
    // Set up WebSocket connection for real-time updates
    const token = localStorage.getItem('token');
    const baseUrl = import.meta.env.VITE_API_BASE_URL || window.location.origin;
    const wsUrl = baseUrl.replace(/^http/, 'ws') + '/ws';
    
    const stompClient = new Client({
      brokerURL: wsUrl,
      connectHeaders: {
        Authorization: `Bearer ${token}`
      },
      debug: function (str) {
        // console.log('STOMP: ' + str);
      },
      onConnect: () => {
        console.log('Connected to WebSocket for real-time updates on group:', id);
        stompClient.subscribe(`/topic/group/${id}`, (message) => {
          console.log('Real-time event received:', message.body);
          // An event happened! Re-fetch everything seamlessly in the background
          fetchGroupData(false);
        });
      },
      onStompError: (frame) => {
        console.error('WebSocket Broker error: ' + frame.headers['message']);
      }
    });

    stompClient.activate();

    return () => {
      stompClient.deactivate();
    };
  }, [id]);

  const fetchGroupData = async (showLoading = true) => {
    if (showLoading) setLoading(true);
    try {
      const [groupRes, expensesRes, balancesRes, settlementsRes] = await Promise.all([
        api.get(`/api/groups/${id}`),
        api.get(`/api/groups/${id}/expenses`),
        api.get(`/api/groups/${id}/balances`),
        api.get(`/api/groups/${id}/settlements`)
      ]);
      setGroup(groupRes.data);
      setExpenses(expensesRes.data);
      setBalances(balancesRes.data);
      
      const merged = [
        ...expensesRes.data.map(e => ({ ...e, type: 'EXPENSE', timestamp: new Date(e.date).getTime() })),
        ...settlementsRes.data.map(s => ({ ...s, type: 'SETTLEMENT', timestamp: new Date(s.createdAt).getTime() }))
      ].sort((a, b) => b.timestamp - a.timestamp);
      setActivityFeed(merged);
    } catch (err) {
      console.error('Failed to fetch group data', err);
      // Navigate back if unauthorized or not found
      navigate('/dashboard');
    } finally {
      setLoading(false);
    }
  };

  const getUserName = (userId) => {
    return group?.members.find(m => m.id === userId)?.name || 'Unknown';
  };

  const handleFileUpload = async (event) => {
    const file = event.target.files[0];
    if (!file) return;
    
    const formData = new FormData();
    formData.append('file', file);
    formData.append('groupId', id);

    try {
      setLoading(true);
      const response = await api.post('/api/expenses/import?confirm=false', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
      
      // Capture the response and show the report modal for PREVIEW
      if (response.data) {
        setStashedCsvFile(file);
        setImportReport(response.data);
      }
    } catch (err) {
      console.error('Failed to upload CSV', err);
      alert('Failed to preview CSV. Please try again.');
    } finally {
      setLoading(false);
      event.target.value = null; // reset input
    }
  };

  const confirmImport = async () => {
    if (!stashedCsvFile) return;

    const formData = new FormData();
    formData.append('file', stashedCsvFile);
    formData.append('groupId', id);

    try {
      setLoading(true);
      setImportReport(null);
      await api.post('/api/expenses/import?confirm=true', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
      alert('CSV successfully imported!');
      await fetchGroupData(false);
    } catch (err) {
      console.error('Failed to confirm CSV import', err);
      alert('Failed to import CSV. Please try again.');
    } finally {
      setLoading(false);
      setStashedCsvFile(null);
    }
  };

  const handleApproveSettlement = async (settlementId) => {
    try {
      setLoading(true);
      await api.put(`/api/groups/${id}/settlements/${settlementId}/approve`);
      await fetchGroupData(false);
    } catch (err) {
      console.error('Failed to approve settlement', err);
      alert('Failed to approve settlement. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const handleRejectSettlement = async (settlementId) => {
    try {
      setLoading(true);
      await api.put(`/api/groups/${id}/settlements/${settlementId}/reject`);
      await fetchGroupData(false);
    } catch (err) {
      console.error('Failed to reject settlement', err);
      alert('Failed to reject settlement. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50 flex flex-col">
        <Navbar />
        <div className="flex-1 flex justify-center items-center">
          <div className="text-gray-500">Loading group details...</div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col">
      <Navbar />
      
      {/* Header section */}
      <div className="bg-white shadow-sm border-b">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
          <button 
            onClick={() => navigate('/dashboard')}
            className="text-emerald-600 hover:text-emerald-700 text-sm font-medium flex items-center mb-4"
          >
            <svg className="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M10 19l-7-7m0 0l7-7m-7 7h18"></path>
            </svg>
            Back to Dashboard
          </button>
          
          <div className="lg:flex lg:items-center lg:justify-between">
            <div className="flex-1 min-w-0">
              <h2 className="text-3xl font-bold leading-7 text-gray-900 sm:truncate">
                {group?.name}
              </h2>
              <div className="mt-1 flex flex-col sm:flex-row sm:flex-wrap sm:mt-0 sm:space-x-6">
                <div 
                  className="mt-2 flex items-center text-sm text-emerald-600 hover:text-emerald-700 cursor-pointer font-medium bg-emerald-50 px-3 py-1 rounded-full border border-emerald-200 transition-colors"
                  onClick={() => setIsViewMembersOpen(true)}
                  title="Click to view all members"
                >
                  <svg className="flex-shrink-0 mr-1.5 h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
                  </svg>
                  {group?.members.length} Members (View)
                </div>
              </div>
            </div>
          <div className="mt-5 flex lg:mt-0 lg:ml-4 space-x-3 items-center">
            <input 
              type="file" 
              accept=".csv" 
              style={{ display: 'none' }} 
              ref={fileInputRef} 
              onChange={handleFileUpload} 
            />
            <button
              onClick={() => fileInputRef.current && fileInputRef.current.click()}
              className="inline-flex items-center px-4 py-2 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 focus:outline-none"
            >
              Upload CSV
            </button>
            <button
              onClick={() => setIsAddMemberOpen(true)}
              className="inline-flex items-center px-4 py-2 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 focus:outline-none"
            >
              Add Member
            </button>
            <button
              onClick={() => setIsSettleUpOpen(true)}
              className="inline-flex items-center px-4 py-2 border border-emerald-600 rounded-md shadow-sm text-sm font-medium text-emerald-700 bg-emerald-50 hover:bg-emerald-100 focus:outline-none"
            >
              Settle Up
            </button>
            <button
              onClick={() => setIsAddExpenseOpen(true)}
              className="inline-flex items-center px-4 py-2 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-emerald-600 hover:bg-emerald-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-emerald-500"
            >
              Add Expense
            </button>
          </div>
        </div>
      </div>
    </div>

      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8 flex flex-col lg:flex-row gap-8">
        
        <div className="lg:w-2/3">
          {/* Pending Approvals Banner */}
          {activityFeed.filter(item => item.type === 'SETTLEMENT' && item.status === 'PENDING' && item.payeeId === user?.id).length > 0 && (
            <div className="bg-amber-50 border-l-4 border-amber-400 p-4 mb-6 rounded shadow-sm">
              <div className="flex items-center">
                <div className="flex-shrink-0">
                  <svg className="h-5 w-5 text-amber-500" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                    <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
                  </svg>
                </div>
                <div className="ml-3">
                  <p className="text-sm text-amber-800 font-medium">
                    You have pending settlements awaiting your approval in the activity feed.
                  </p>
                </div>
              </div>
            </div>
          )}

          <h3 className="text-lg font-medium text-gray-900 mb-4">Recent Activity</h3>
          <div className="bg-white shadow overflow-hidden sm:rounded-md border border-gray-100">
            {activityFeed.length === 0 ? (
              <div className="text-center py-10">
                <p className="text-sm text-gray-500">No activity yet. Add an expense or settle up to get started!</p>
              </div>
            ) : (
              <ul className="divide-y divide-gray-200">
                {activityFeed.map((item) => (
                  <li key={`${item.type}-${item.id}`}>
                    {item.type === 'EXPENSE' ? (
                      <div 
                        onClick={() => navigate(`/expenses/${item.id}`)}
                        className="block hover:bg-gray-50 transition cursor-pointer"
                      >
                        <div className="px-4 py-4 sm:px-6 flex items-center justify-between">
                          <div className="flex flex-col">
                            <p className="text-sm font-bold text-gray-900 truncate">{item.description}</p>
                            <p className="text-xs text-gray-500 mt-1">
                              {getUserName(item.payerId)} paid <span className="font-medium text-gray-900">₹{item.amount.toFixed(2)}</span>
                            </p>
                          </div>
                          <div className="flex flex-col items-end">
                            <p className="text-xs text-gray-500">{new Date(item.date).toLocaleDateString()}</p>
                            <p className="text-xs font-semibold text-emerald-600 mt-1 uppercase">
                              {item.splitType}
                            </p>
                          </div>
                        </div>
                      </div>
                    ) : (
                      <div className={`block ${item.status === 'PENDING' ? 'bg-amber-50 border-l-4 border-amber-400' : item.status === 'REJECTED' ? 'bg-red-50 border-l-4 border-red-400' : 'bg-emerald-50 border-l-4 border-emerald-500'} bg-opacity-50`}>
                        <div className="px-4 py-4 sm:px-6 flex items-center justify-between">
                          <div className="flex items-center">
                            <div className={`flex-shrink-0 rounded-full p-2 mr-3 ${item.status === 'PENDING' ? 'bg-amber-100' : item.status === 'REJECTED' ? 'bg-red-100' : 'bg-emerald-100'}`}>
                              {item.status === 'PENDING' ? (
                                <svg className="h-4 w-4 text-amber-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                                </svg>
                              ) : item.status === 'REJECTED' ? (
                                <svg className="h-4 w-4 text-red-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12" />
                                </svg>
                              ) : (
                                <svg className="h-4 w-4 text-emerald-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M5 13l4 4L19 7" />
                                </svg>
                              )}
                            </div>
                            <div className="flex flex-col">
                              <p className={`text-sm font-medium ${item.status === 'PENDING' ? 'text-amber-800' : item.status === 'REJECTED' ? 'text-red-800 line-through' : 'text-emerald-800'}`}>
                                {getUserName(item.payerId)} {item.status === 'REJECTED' ? 'tried to settle up with' : 'settled up with'} {getUserName(item.payeeId)}
                              </p>
                              <p className={`text-xs mt-1 font-semibold ${item.status === 'PENDING' ? 'text-amber-600' : item.status === 'REJECTED' ? 'text-red-600' : 'text-emerald-600'}`}>
                                Paid ₹{item.amount.toFixed(2)}
                              </p>
                            </div>
                          </div>
                          <div className="flex flex-col items-end">
                            <p className="text-xs text-gray-500">{new Date(item.createdAt).toLocaleDateString()}</p>
                            <div className="mt-1 flex space-x-2">
                              {item.status === 'PENDING' && item.payeeId === user?.id && (
                                <>
                                  <button onClick={() => handleApproveSettlement(item.id)} className="text-xs font-bold text-white bg-emerald-500 hover:bg-emerald-600 px-2 py-1 rounded">APPROVE</button>
                                  <button onClick={() => handleRejectSettlement(item.id)} className="text-xs font-bold text-white bg-red-500 hover:bg-red-600 px-2 py-1 rounded">REJECT</button>
                                </>
                              )}
                              {(item.status !== 'PENDING' || item.payeeId !== user?.id) && (
                                <p className={`text-xs font-semibold uppercase ${item.status === 'PENDING' ? 'text-amber-600' : item.status === 'REJECTED' ? 'text-red-600' : 'text-emerald-600'}`}>
                                  {item.status}
                                </p>
                              )}
                            </div>
                          </div>
                        </div>
                      </div>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>

        {/* Right column: Balances Widget */}
        <div className="lg:w-1/3">
          <div className="bg-white shadow sm:rounded-lg border border-gray-100 p-6 sticky top-6">
            <h3 className="text-lg font-medium text-gray-900 border-b pb-3">Group Balances</h3>
            
            <div className="mt-4 mb-6">
              <p className="text-sm text-gray-500">Your Net Balance</p>
              <p className={`text-2xl font-bold ${balances?.netBalance > 0 ? 'text-emerald-600' : balances?.netBalance < 0 ? 'text-red-600' : 'text-gray-900'}`}>
                ₹{balances ? Math.abs(balances.netBalance).toFixed(2) : '0.00'}
                {balances?.netBalance > 0 ? ' (Owed)' : balances?.netBalance < 0 ? ' (Owe)' : ''}
              </p>
            </div>

            <div>
              <p className="text-sm font-medium text-gray-700 mb-3">Simplified Debts</p>
              {(!balances?.simplifiedDebts || balances.simplifiedDebts.length === 0) ? (
                <p className="text-sm text-gray-500 italic">Everyone is settled up.</p>
              ) : (
                <ul className="space-y-3">
                  {balances.simplifiedDebts.map((debt, idx) => {
                    const isCurrentUserFrom = debt.from === user?.id;
                    const isCurrentUserTo = debt.to === user?.id;
                    
                    return (
                      <li 
                        key={idx} 
                        className="flex items-center text-sm bg-gray-50 p-2 rounded cursor-pointer hover:bg-emerald-50 transition-colors border border-transparent hover:border-emerald-200 group"
                        onClick={() => setSelectedDebtForAudit(debt)}
                        title="Click to view detailed audit trail of this debt"
                      >
                        <span className="flex-1">
                          <span className={isCurrentUserFrom ? 'font-bold' : ''}>{getUserName(debt.from)}</span> 
                          {' owes '}
                          <span className={isCurrentUserTo ? 'font-bold' : ''}>{getUserName(debt.to)}</span>
                        </span>
                        <span className={`font-bold ${isCurrentUserFrom ? 'text-red-600' : isCurrentUserTo ? 'text-emerald-600' : 'text-gray-900'} group-hover:underline`}>
                          ₹{debt.amount.toFixed(2)}
                        </span>
                      </li>
                    );
                  })}
                </ul>
              )}
            </div>
          </div>
        </div>

      </main>

      {/* Modals */}
      <AddMemberModal 
        isOpen={isAddMemberOpen} 
        onClose={() => setIsAddMemberOpen(false)} 
        groupId={id}
        onMemberAdded={(updatedGroup) => {
          setGroup(updatedGroup);
        }}
      />
      <AddExpenseModal
        isOpen={isAddExpenseOpen}
        onClose={() => setIsAddExpenseOpen(false)}
        groupId={id}
        members={group?.members || []}
        currentUser={user}
        onExpenseAdded={(newExpense) => {
          setExpenses([newExpense, ...expenses]);
          // Refresh balances after adding an expense
          fetchGroupData(false);
        }}
      />
      <SettleUpModal
        isOpen={isSettleUpOpen}
        onClose={() => setIsSettleUpOpen(false)}
        groupId={id}
        simplifiedDebts={balances?.simplifiedDebts || []}
        currentUser={user}
        members={group?.members || []}
        onSettlementComplete={() => {
          // Refresh everything after a settlement
          fetchGroupData(false);
        }}
      />
      
      <ImportReportModal
        isOpen={!!importReport}
        onClose={() => { setImportReport(null); setStashedCsvFile(null); }}
        report={importReport}
        onApprove={confirmImport}
      />
      <AuditTrailModal
        isOpen={!!selectedDebtForAudit}
        onClose={() => setSelectedDebtForAudit(null)}
        groupId={id}
        user1Id={selectedDebtForAudit?.from}
        user2Id={selectedDebtForAudit?.to}
        user1Name={selectedDebtForAudit ? getUserName(selectedDebtForAudit.from) : ''}
        user2Name={selectedDebtForAudit ? getUserName(selectedDebtForAudit.to) : ''}
        simplifiedAmount={selectedDebtForAudit?.amount}
      />
      <ViewMembersModal
        isOpen={isViewMembersOpen}
        onClose={() => setIsViewMembersOpen(false)}
        members={group?.members || []}
      />
    </div>
  );
}
