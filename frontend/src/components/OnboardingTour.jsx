import { X, LayoutDashboard, ShieldAlert, Bot, Wrench, ArrowRight, Check } from 'lucide-react';
import { useState } from 'react';

const steps = [
  {
    id: 'dashboard',
    title: 'Executive Dashboard',
    description: 'Get a bird\'s-eye view of your security posture. Track finding severity, historical trends, and audit context at a glance.',
    icon: LayoutDashboard,
    color: 'text-indigo-400',
    bg: 'bg-indigo-400/10'
  },
  {
    id: 'findings',
    title: 'Findings Inventory',
    description: 'Drill down into specific vulnerabilities. Use powerful filters, bulk triaging, and side-by-side diffs to manage your risk.',
    icon: ShieldAlert,
    color: 'text-rose-400',
    bg: 'bg-rose-400/10'
  },
  {
    id: 'chat',
    title: 'AI Chat Agent',
    description: 'Have questions about a vulnerability? Chat directly with the AI contextually aware of your entire repository and audit results.',
    icon: Bot,
    color: 'text-cyan-400',
    bg: 'bg-cyan-400/10'
  },
  {
    id: 'remediation',
    title: 'Autonomous Remediation',
    description: 'One-click fixes. The AI agent patches the code, compiles it, verifies the fix, and automatically opens a Pull Request.',
    icon: Wrench,
    color: 'text-emerald-400',
    bg: 'bg-emerald-400/10'
  }
];

export function OnboardingTour({ isOpen, onClose }) {
  const [currentStep, setCurrentStep] = useState(0);

  if (!isOpen) return null;

  const step = steps[currentStep];
  const Icon = step.icon;

  const handleNext = () => {
    if (currentStep < steps.length - 1) {
      setCurrentStep(curr => curr + 1);
    } else {
      onClose();
      setCurrentStep(0);
    }
  };

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center bg-[#040e18]/80 backdrop-blur-sm animate-fade-in p-4">
      <div className="relative w-full max-w-lg rounded-3xl border border-slate-700/50 bg-[#081522] p-8 shadow-2xl overflow-hidden">
         {/* Background decoration */}
         <div className={`absolute -right-20 -top-20 h-64 w-64 rounded-full blur-3xl opacity-20 transition-colors duration-500 ${step.bg.replace('/10', '')}`} />
         
         <button onClick={() => { onClose(); setCurrentStep(0); }} className="absolute right-6 top-6 text-slate-500 hover:text-slate-300 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400 rounded-md">
            <X size={20} />
         </button>

         <div className="relative flex flex-col items-center text-center">
            <div className={`flex h-20 w-20 items-center justify-center rounded-2xl ${step.bg} mb-6 transition-colors duration-500`}>
              <Icon size={40} className={step.color} />
            </div>
            
            <h2 className="text-2xl font-bold text-white mb-3">{step.title}</h2>
            <p className="text-sm leading-relaxed text-slate-400 mb-8 min-h-[60px]">
              {step.description}
            </p>

            <div className="flex w-full items-center justify-between">
              <div className="flex gap-2">
                {steps.map((s, i) => (
                  <div key={s.id} className={`h-1.5 rounded-full transition-all duration-300 ${i === currentStep ? 'w-6 bg-cyan-400' : 'w-1.5 bg-slate-700'}`} />
                ))}
              </div>
              <button onClick={handleNext} className="flex items-center gap-2 rounded-xl bg-cyan-500 px-5 py-2.5 text-sm font-bold text-[#04202b] transition hover:bg-cyan-400 hover:scale-105 active:scale-95 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-300">
                {currentStep === steps.length - 1 ? (
                  <>Get Started <Check size={16} /></>
                ) : (
                  <>Next <ArrowRight size={16} /></>
                )}
              </button>
            </div>
         </div>
      </div>
    </div>
  );
}
