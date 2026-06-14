import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import api from '../services/api';
import Navbar from '../components/Navbar';
import ChatWidget from '../components/ChatWidget';

export default function ExpenseDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  
  const [expense, setExpense] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchExpense = async () => {
      try {
        const res = await api.get(`/api/expenses/${id}`);
        setExpense(res.data);
      } catch (err) {
        console.error('Failed to fetch expense details', err);
        navigate('/dashboard');
      } finally {
        setLoading(false);
      }
    };

    fetchExpense();
  }, [id, navigate]);

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50 flex flex-col">
        <Navbar />
        <div className="flex-1 flex justify-center items-center">
          <div className="text-gray-500">Loading expense details...</div>
        </div>
      </div>
    );
  }

  if (!expense) return null;

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col">
      <Navbar />
      
      {/* Header section */}
      <div className="bg-white shadow-sm border-b">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
          <button 
            onClick={() => navigate(-1)}
            className="text-emerald-600 hover:text-emerald-700 text-sm font-medium flex items-center mb-4"
          >
            <svg className="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M10 19l-7-7m0 0l7-7m-7 7h18"></path>
            </svg>
            Back
          </button>
          
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-3xl font-bold leading-7 text-gray-900">
                {expense.description}
              </h2>
              <div className="mt-2 flex items-center text-sm text-gray-500 space-x-4">
                <span>{new Date(expense.date).toLocaleDateString()}</span>
                <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-800 uppercase">
                  {expense.splitType}
                </span>
              </div>
            </div>
            <div className="text-right">
              <p className="text-sm text-gray-500">Total Amount</p>
              <p className="text-3xl font-bold text-gray-900">₹{expense.amount.toFixed(2)}</p>
            </div>
          </div>
        </div>
      </div>

      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="flex flex-col lg:flex-row gap-8 h-[calc(100vh-250px)] min-h-[500px]">
          
          {/* Left Column: Split Breakdown */}
          <div className="lg:w-1/2 flex flex-col">
            <h3 className="text-lg font-medium text-gray-900 mb-4">Split Breakdown</h3>
            <div className="bg-white shadow overflow-hidden sm:rounded-md border border-gray-100 flex-1">
              <ul className="divide-y divide-gray-200">
                {expense.splits.map((split, idx) => {
                  const isPayer = split.userId === expense.payerId;
                  return (
                    <li key={idx} className="px-6 py-4 flex items-center justify-between hover:bg-gray-50">
                      <div className="flex items-center">
                        <div className="h-10 w-10 rounded-full bg-gray-200 flex items-center justify-center text-gray-500 font-bold">
                          <svg className="h-6 w-6" fill="currentColor" viewBox="0 0 24 24">
                            <path d="M24 20.993V24H0v-2.996A14.977 14.977 0 0112.004 15c4.904 0 9.26 2.354 11.996 5.993zM16.002 8.999a4 4 0 11-8 0 4 4 0 018 0z" />
                          </svg>
                        </div>
                        <div className="ml-4">
                          <p className="text-sm font-medium text-gray-900 flex items-center">
                            {split.userName || `User ${split.userId.substring(0, 8)}...`}
                            {expense.splitType === 'PERCENTAGE' && (
                              <span className="ml-2 text-sm text-gray-500 font-normal">
                                ({((split.amountOwed / expense.amount) * 100).toFixed(2)}%)
                              </span>
                            )}
                            {isPayer && (
                              <span className="ml-2 px-2 inline-flex text-xs leading-5 font-semibold rounded-full bg-emerald-100 text-emerald-800">
                                Payer
                              </span>
                            )}
                          </p>
                        </div>
                      </div>
                      <div className="text-right">
                        {isPayer ? (
                          <>
                            <p className="text-sm font-bold text-emerald-600">₹{expense.amount.toFixed(2)}</p>
                            <p className="text-xs text-gray-500">Paid (Owes ₹{split.amountOwed.toFixed(2)})</p>
                          </>
                        ) : (
                          <>
                            <p className="text-sm font-bold text-red-600">₹{split.amountOwed.toFixed(2)}</p>
                            <p className="text-xs text-gray-500">Owes</p>
                          </>
                        )}
                      </div>
                    </li>
                  );
                })}
              </ul>
            </div>
          </div>

          {/* Right Column: Real-Time Chat */}
          <div className="lg:w-1/2 flex flex-col h-full">
            <ChatWidget expenseId={expense.id} />
          </div>

        </div>
      </main>
    </div>
  );
}
