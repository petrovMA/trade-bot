import { BrowserRouter as Router, Routes, Route, Link, useLocation } from 'react-router-dom';
import Dashboard from '@pages/Dashboard';
import BotManagement from '@pages/BotManagement';
import CreateBot from '@pages/CreateBot';
import BotConfigs from '@pages/BotConfigs';
import BotDetail from '@pages/BotDetail';

function Navigation() {
  const location = useLocation();

  const isActive = (path: string) => {
    return location.pathname === path ? 'bg-blue-100 text-blue-700' : 'text-gray-700 hover:bg-gray-100';
  };

  return (
    <nav className="bg-white border-b border-gray-200">
      <div className="max-w-7xl mx-auto px-4">
        <div className="flex space-x-4 py-3">
          <Link to="/" className={`px-3 py-2 rounded-md text-sm font-medium ${isActive('/')}`}>
            Dashboard
          </Link>
          <Link to="/configs" className={`px-3 py-2 rounded-md text-sm font-medium ${isActive('/configs')}`}>
            Bot Configs
          </Link>
          <Link to="/bots" className={`px-3 py-2 rounded-md text-sm font-medium ${isActive('/bots')}`}>
            Bot Management
          </Link>
          <Link to="/bots/create" className={`px-3 py-2 rounded-md text-sm font-medium ${isActive('/bots/create')}`}>
            Create Bot
          </Link>
          <a href="/trade-bot/grid-analysis" className="px-3 py-2 rounded-md text-sm font-medium text-green-700 hover:bg-green-100 border border-green-300">
            Grid Analysis
          </a>
        </div>
      </div>
    </nav>
  );
}

function App() {
  return (
    <Router basename="/trade-bot">
      <div className="min-h-screen bg-gray-50">
        <header className="bg-white shadow">
          <div className="max-w-7xl mx-auto px-4 py-6">
            <h1 className="text-3xl font-bold text-gray-900">Trade Bot Management</h1>
          </div>
        </header>
        <Navigation />
        <main className="max-w-7xl mx-auto px-4 py-6">
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/configs" element={<BotConfigs />} />
            <Route path="/configs/:botName" element={<BotDetail />} />
            <Route path="/bots" element={<BotManagement />} />
            <Route path="/bots/create" element={<CreateBot />} />
          </Routes>
        </main>
      </div>
    </Router>
  );
}

export default App;
