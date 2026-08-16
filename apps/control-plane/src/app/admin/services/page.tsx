import { ManagedLicenseForm } from "@/components/ManagedLicenseForm";

export const dynamic = "force-dynamic";

export default function ServicesPage() {
  return (
    <>
      <header className="page-header">
        <div>
          <h1>کاربران و مجوزها</h1>
          <p>صدور، مسدودی، حجم، تمدید، دستگاه و گروه سرورها در یک صفحه</p>
        </div>
      </header>
      <ManagedLicenseForm />
    </>
  );
}
