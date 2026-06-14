import React from 'react';

const ImportReportModal = ({ isOpen, onClose, report, onApprove }) => {
  if (!isOpen || !report) return null;

  return (
    <div className="fixed inset-0 bg-gray-500 bg-opacity-75 flex items-center justify-center p-4 z-50">
      <div className="bg-white rounded-lg shadow-xl max-w-2xl w-full max-h-[90vh] flex flex-col overflow-hidden">
        
        <div className="px-6 py-4 border-b border-gray-200">
          <h3 className="text-lg font-medium text-gray-900">
            Import Preview (Action Required)
          </h3>
          <p className="mt-1 text-sm text-amber-600 font-medium">
            Review the following changes. No data has been saved to the database yet.
            Processed {report.totalProcessed} total records from the CSV file.
          </p>
        </div>

        <div className="px-6 py-4 overflow-y-auto flex-1">
          {report.anomaliesDetected && report.anomaliesDetected.length > 0 && (
            <div className="mb-6">
              <h4 className="text-md font-medium text-red-600 mb-2 flex items-center">
                <svg className="w-5 h-5 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                </svg>
                Anomalies & Rejections ({report.anomaliesDetected.length})
              </h4>
              <ul className="bg-red-50 border border-red-100 rounded-md p-3 text-sm text-red-800 space-y-1">
                {report.anomaliesDetected.map((anomaly, idx) => (
                  <li key={idx} className="flex items-start">
                    <span className="mr-2">•</span>
                    <span>{anomaly}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}

          {report.successfulImports && report.successfulImports.length > 0 && (
            <div>
              <h4 className="text-md font-medium text-emerald-600 mb-2 flex items-center">
                <svg className="w-5 h-5 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                Successful Imports ({report.successfulImports.length})
              </h4>
              <ul className="bg-emerald-50 border border-emerald-100 rounded-md p-3 text-sm text-emerald-800 space-y-1">
                {report.successfulImports.map((success, idx) => (
                  <li key={idx} className="flex items-start">
                    <span className="mr-2">✓</span>
                    <span>{success}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}

          {report.anomaliesDetected?.length === 0 && report.successfulImports?.length === 0 && (
            <p className="text-sm text-gray-500 italic text-center py-4">No specific details returned in the report.</p>
          )}
        </div>

        <div className="px-6 py-4 border-t border-gray-200 bg-gray-50 flex justify-end space-x-3">
          <button
            onClick={onClose}
            className="px-4 py-2 bg-white border border-gray-300 text-gray-700 rounded hover:bg-gray-50 font-medium"
          >
            Cancel
          </button>
          <button
            onClick={onApprove}
            className="px-4 py-2 bg-emerald-600 text-white rounded hover:bg-emerald-700 font-medium"
          >
            Approve & Import
          </button>
        </div>

      </div>
    </div>
  );
};

export default ImportReportModal;
